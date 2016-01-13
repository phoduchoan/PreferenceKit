/*
 *  Copyright 2016 MajeurAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.majeur.preferencekit;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public abstract class PreferenceHeadersActivity extends AppCompatActivity implements HeaderListFragmentCallback {

    public static class Header {

        public static Header createHeaderRow(CharSequence title, CharSequence summary, int iconRes, boolean tintIcon, CharSequence fragmentName) {
            return createHeaderRow(title, summary, iconRes, tintIcon, fragmentName, 0, -1);
        }

        public static Header createHeaderRow(CharSequence title, CharSequence summary, int iconRes, boolean tintIcon, CharSequence fragmentName, int headerId) {
            return createHeaderRow(title, summary, iconRes, tintIcon, fragmentName, headerId, -1);
        }

        public static Header createHeaderRow(CharSequence title, CharSequence summary, int iconRes, boolean tintIcon, CharSequence fragmentName, int headerId, int color) {
            return new Header(title, summary, iconRes, tintIcon, fragmentName, false, headerId, color);
        }

        public static Header createHeaderSeparator() {
            return createHeaderSeparator(null);
        }

        public static Header createHeaderSeparator(CharSequence title) {
            return new Header(title, null, 0, false, null, true, 0, -1);
        }

        private Header() {
            this.color = -1;
        }

        private Header(CharSequence title, CharSequence summary, int iconRes, boolean tintIcon, CharSequence fragmentName, boolean isSeparator,
                       int id, int color) {
            this.title = title;
            this.summary = summary;
            this.iconRes = iconRes;
            this.tintIcon = tintIcon;
            this.fragmentName = fragmentName;
            this.isSeparator = isSeparator;
            this.color = color;
            this.id = id;
        }

        CharSequence title;
        CharSequence summary;
        int iconRes;
        boolean tintIcon;
        CharSequence fragmentName;
        boolean isSeparator;
        int color;
        int id;

        public int getHeaderId() {
            return id;
        }

        public void setColor(int color) {
            this.color = color;
        }
    }

    public static final int STATE_HEADER_LIST = 0;
    public static final int STATE_HEADER_OPENED = 1;

    private int mDecorColor;
    private int mState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preference_headers);

        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.pk_fragments_frame, new HeaderListFragment(), HeaderListFragment.FRAGMENT_TAG)
                    .commit();
            getFragmentManager().executePendingTransactions();
        }

        setDecorColor(Utils.getAttrColor(this, R.attr.colorAccent));
    }

    private HeaderListFragment getHeaderListFragment() {
        return (HeaderListFragment) getFragmentManager().findFragmentByTag(HeaderListFragment.FRAGMENT_TAG);
    }

    public void addHeadersFromXml(int xmlResId) {
        getHeaderListFragment().addHeadersFromXml(xmlResId);
    }

    public ListView getHeadersListView() {
        return getHeaderListFragment().getListView();
    }

    public int getHeaderCount() {
        return getHeaderListFragment().getHeaderCount();
    }

    public Header getHeaderAt(int position) {
        return getHeaderListFragment().getHeaderAt(position);
    }

    public void addHeader(Header header) {
        addHeaderAt(header, -1);
    }

    public void addHeaderAt(Header header, int position) {
        getHeaderListFragment().addHeaderAt(header, position);
    }

    public void notifyHeadersChanged() {
        getHeaderListFragment().notifyHeadersChanged();
    }

    public void setDecorColor(int color) {
        mDecorColor = color;
        getHeaderListFragment().setDecorColor(color);
    }

    public int getDecorColor() {
        return mDecorColor;
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0)
            getFragmentManager().popBackStack();
        else
            super.onBackPressed();
    }

    private void syncState(int state) {
        mState = state;
        if (state == STATE_HEADER_LIST) {
            String defaultTitle = onGetDefaultTitle();
            if (defaultTitle != null)
                setTitle(defaultTitle);
        }
        onStateChanged(state);
    }

    public int getState() {
        return mState;
    }

    protected void onStateChanged(int newState) {
    }

    @Override
    public void onHeaderClicked(PreferenceHeadersActivity.Header header) {
        CharSequence fragmentName = header.fragmentName;

        if (!TextUtils.isEmpty(fragmentName)) {
            Fragment fragment = Fragment.instantiate(PreferenceHeadersActivity.this, fragmentName.toString());

            if (!(fragment instanceof PreferenceFragment))
                throw new IllegalStateException("Fragment should be instance of PreferenceFragment");

            setTitle(header.title);
            syncState(STATE_HEADER_OPENED);
            startFragment(fragment);
        }
    }

    private void startFragment(Fragment fragment) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();

        transaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        transaction.addToBackStack(fragment.toString());
        transaction.replace(R.id.pk_fragments_frame, fragment);
        transaction.commit();
    }

    protected abstract String onGetDefaultTitle();

    public static class HeaderListFragment extends Fragment {

        static final String FRAGMENT_TAG = "header_list_fragment";

        private List<Header> mHeaders = new LinkedList<>();
        private ListView mListView;

        private int mDecorColor;

        @Override
        public void onDestroy() {
            super.onDestroy();
            mHeaders.clear();
            mHeaders = null;
        }

        @Override
        public void onStart() {
            super.onStart();
            ((PreferenceHeadersActivity) getActivity()).syncState(STATE_HEADER_LIST);
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            mListView = new ListView(getActivity());
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mListView.setLayoutParams(params);
            mListView.setDivider(null);

            mListView.setAdapter(mAdapter);
            mListView.setOnItemClickListener(mOnItemClickListener);

            return mListView;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            mListView.setAdapter(null);
            mListView = null;
        }

        public void setDecorColor(int color) {
            mDecorColor = color;
            mAdapter.notifyDataSetChanged();
        }

        public ListView getListView() {
            return mListView;
        }

        private AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Header header = mHeaders.get(i);
                ((HeaderListFragmentCallback) getActivity()).onHeaderClicked(header);
            }
        };

        private BaseAdapter mAdapter = new BaseAdapter() {

            final int VIEW_TYPE_SEPARATOR = 0;
            final int VIEW_TYPE_HEADER = 1;

            @Override
            public int getCount() {
                return mHeaders == null ? 0 : mHeaders.size();
            }

            @Override
            public Header getItem(int i) {
                return mHeaders.get(i);
            }

            @Override
            public long getItemId(int i) {
                return getItem(i).hashCode();
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int position) {
                return getItem(position).isSeparator ? VIEW_TYPE_SEPARATOR : VIEW_TYPE_HEADER;
            }

            class ViewHolder {
                ImageView icon;
                TextView title;
                TextView summary;
                View shadowSeparator;
                View divider;
                int viewType;
            }

            @Override
            public View getView(int position, View view, ViewGroup parent) {
                final int viewType = getItemViewType(position);
                final boolean isSeparator = viewType == VIEW_TYPE_SEPARATOR;
                final boolean createNewView = view == null || ((ViewHolder) view.getTag()).viewType != viewType;

                ViewHolder holder;
                if (createNewView) {
                    view = getActivity().getLayoutInflater().inflate(isSeparator ? R.layout.header_separator : R.layout.header_row,
                            parent, false);
                    holder = new ViewHolder();
                    holder.viewType = viewType;
                    holder.title = (TextView) view.findViewById(R.id.pk_title);

                    if (isSeparator) {
                        holder.title.setTypeface(Utils.Typefaces
                                .getRobotoMedium(getActivity()));
                        holder.title.setTextColor(mDecorColor);
                        holder.shadowSeparator = view.findViewById(R.id.pk_shadows);
                    } else {
                        holder.summary = (TextView) view.findViewById(R.id.pk_summary);
                        holder.icon = (ImageView) view.findViewById(R.id.pk_icon);
                        holder.divider = view.findViewById(R.id.pk_divider);
                    }
                    view.setTag(holder);
                } else {
                    holder = (ViewHolder) view.getTag();
                }

                Header header = getItem(position);

                if (header.title != null) {
                    holder.title.setText(header.title);
                    holder.title.setVisibility(View.VISIBLE);

                    if (!isSeparator)
                        holder.title.setTextColor(header.color == -1 ? Color.BLACK : Color.WHITE);
                } else {
                    holder.title.setVisibility(View.GONE);
                }

                if (isSeparator) {
                    if (position == 0) {
                        // Remove shadowed offset separation if first item is a separator
                        holder.shadowSeparator.setVisibility(View.GONE);
                    } else {
                        holder.shadowSeparator.setVisibility(View.VISIBLE);
                    }

                } else {
                    if (header.summary != null) {
                        holder.summary.setText(header.summary);
                        holder.summary.setVisibility(View.VISIBLE);
                        holder.summary.setTextColor(header.color == -1 ? Color.BLACK : Color.WHITE);

                    } else {
                        holder.summary.setVisibility(View.GONE);
                    }

                    if (header.iconRes != 0) {
                        holder.icon.setImageResource(header.iconRes);
                        holder.icon.setVisibility(View.VISIBLE);

                        if (header.tintIcon)
                            holder.icon.setColorFilter(mDecorColor, PorterDuff.Mode.SRC_IN);
                        else
                            holder.icon.clearColorFilter();
                    } else {
                        holder.icon.setVisibility(View.GONE);
                    }

                    holder.divider.setVisibility(getDividerVisibility(position));
                }

                if (header.color != -1)
                    view.setBackgroundColor(header.color);
                else
                    view.setBackgroundDrawable(null);

                return view;
            }

            int getDividerVisibility(int position) {
                if (position == (getCount() - 1))
                    return View.VISIBLE;
                else if (getItem(position).color != -1)
                    return View.GONE;

                Header header = getItem(position + 1);
                return header.isSeparator ? View.GONE : View.VISIBLE;
            }

            @Override
            public boolean isEnabled(int position) {
                return getItemViewType(position) == VIEW_TYPE_HEADER;
            }
        };

        void notifyHeadersChanged() {
            mAdapter.notifyDataSetChanged();
        }

        int getHeaderCount() {
            return mHeaders.size();
        }

        Header getHeaderAt(int position) {
            return mHeaders.get(position);
        }

        void addHeaderAt(Header header, int position) {
            if (position == -1)
                mHeaders.add(header);
            else
                mHeaders.add(position, header);

            notifyHeadersChanged();
        }

        void addHeadersFromXml(int xmlResId) {
            Resources resources = getResources();
            List<Header> localList = new LinkedList<>();
            XmlResourceParser parser = null;

            try {
                parser = resources.getXml(xmlResId);
                AttributeSet attrs = Xml.asAttributeSet(parser);

                int type;
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && type != XmlPullParser.START_TAG) {
                    // Parse next until start tag is found
                }

                String nodeName = parser.getName();
                if (!"headers".equals(nodeName)) {
                    throw new RuntimeException(
                            "XML document must start with <headers> tag; found "
                                    + nodeName + " at " + parser.getPositionDescription());
                }

                final int outerDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }

                    nodeName = parser.getName();
                    if ("header".equals(nodeName)) {
                        Header header = new Header();

                        TypedArray typedArray = getActivity().obtainStyledAttributes(attrs, R.styleable.PreferenceHeader);

                        TypedValue typedValue = typedArray.peekValue(R.styleable.PreferenceHeader_title);
                        if (typedValue != null && typedValue.type == TypedValue.TYPE_STRING) {
                            if (typedValue.resourceId != 0) {
                                header.title = resources.getText(typedValue.resourceId);
                            } else {
                                header.title = typedValue.string;
                            }
                        }

                        typedValue = typedArray.peekValue(R.styleable.PreferenceHeader_summary);
                        if (typedValue != null && typedValue.type == TypedValue.TYPE_STRING) {
                            if (typedValue.resourceId != 0) {
                                header.summary = resources.getText(typedValue.resourceId);
                            } else {
                                header.summary = typedValue.string;
                            }
                        }

                        header.id = typedArray.getResourceId(R.styleable.PreferenceHeader_id, 0);
                        header.iconRes = typedArray.getResourceId(R.styleable.PreferenceHeader_icon, 0);
                        header.fragmentName = typedArray.getString(R.styleable.PreferenceHeader_fragment);
                        header.tintIcon = typedArray.getBoolean(R.styleable.PreferenceHeader_tintIcon, false);
                        typedArray.recycle();

                        final int innerDepth = parser.getDepth();
                        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                                && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {

                            // Skip inner fragment args
                        }

                        localList.add(header);
                    } else if ("separator".equals(nodeName)) {
                        Header header = new Header();
                        header.isSeparator = true;

                        TypedArray typedArray = getActivity().obtainStyledAttributes(attrs, R.styleable.PreferenceHeader);

                        TypedValue typedValue = typedArray.peekValue(R.styleable.PreferenceHeader_title);
                        if (typedValue != null && typedValue.type == TypedValue.TYPE_STRING) {
                            if (typedValue.resourceId != 0) {
                                header.title = resources.getText(typedValue.resourceId);
                            } else {
                                header.title = typedValue.string;
                            }
                        }

                        header.id = typedArray.getResourceId(R.styleable.PreferenceHeader_id, 0);

                        typedArray.recycle();

                        localList.add(header);
                    } else {
                        Utils.skipCurrentTag(parser);
                    }
                }

            } catch (XmlPullParserException e) {
                throw new RuntimeException("Error parsing headers", e);
            } catch (IOException e) {
                throw new RuntimeException("Error parsing headers", e);
            } finally {
                if (parser != null)
                    parser.close();
            }

            mHeaders.addAll(localList);
            notifyHeadersChanged();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            if (mListView != null) {
                int index = mListView.getFirstVisiblePosition();
                View firstChild = mListView.getChildAt(0);
                int top = (firstChild == null) ? 0 : firstChild.getTop();

                outState.putInt("index", index);
                outState.putInt("top", top);
            }
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null && mListView != null) {
                int index = savedInstanceState.getInt("index", -1);
                int top = savedInstanceState.getInt("top", 0);

                if (index != -1)
                    mListView.setSelectionFromTop(index, top);
            }
        }
    }
}

// Not usual to define interfaces here, but interfaces cannot be define in classes which implement them.
// A separate file would be inconvenient.
interface HeaderListFragmentCallback {
    void onHeaderClicked(PreferenceHeadersActivity.Header header);
}
