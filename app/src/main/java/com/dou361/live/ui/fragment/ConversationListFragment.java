package com.dou361.live.ui.fragment;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.dou361.live.R;
import com.dou361.live.bean.AnchorBean;
import com.dou361.live.ui.activity.ChatActivity;
import com.dou361.live.ui.config.StatusConfig;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.easeui.controller.EaseUI;
import com.hyphenate.easeui.utils.EaseCommonUtils;
import com.hyphenate.easeui.widget.EaseConversationList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * ========================================
 * <p>
 * 版 权：dou361.com 版权所有 （C） 2015
 * <p>
 * 作 者：陈冠明
 * <p>
 * 个人网站：http://www.dou361.com
 * <p>
 * 版 本：1.0
 * <p>
 * 创建日期：2016/10/7 12:42
 * <p>
 * 描 述：聊天会话列表对象
 * <p>
 * <p>
 * 修订历史：
 * <p>
 * ========================================
 */
public class ConversationListFragment extends BaseFragment implements EMMessageListener {

    @BindView(R.id.conversation_list)
    EaseConversationList conversationListView;
    private List<EMConversation> conversationList = new ArrayList<>();
    private AnchorBean anchorBean;
    /**
     * 是否是正常样式true为整个页面，false为对话框样式
     */
    private boolean isNormalStyle;

    public ConversationListFragment() {
    }

    public static ConversationListFragment newInstance(AnchorBean anchorBean, boolean isNormalStyle) {
        ConversationListFragment fragment = new ConversationListFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(StatusConfig.ARG_ANCHOR, anchorBean);
        bundle.putBoolean(StatusConfig.ARG_IS_NORMAL, isNormalStyle);
        fragment.setArguments(bundle);
        return fragment;
    }


    @Override
    public View initView(LayoutInflater inflater, ViewGroup container) {
        View view = inflater.inflate(R.layout.fragment_conversation_list, container, false);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (getArguments() != null) {
            anchorBean = (AnchorBean) getArguments().getSerializable(StatusConfig.ARG_ANCHOR);
            isNormalStyle = getArguments().getBoolean(StatusConfig.ARG_IS_NORMAL, false);
        }

        conversationList.clear();
        conversationList.addAll(loadConversationList());
        conversationListView.init(conversationList);
        conversationListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AnchorBean anchorBean = new AnchorBean();
                anchorBean.setName(conversationList.get(position).getUserName());
                anchorBean.setAnchorId(conversationList.get(position).getUserName());
                if (!isNormalStyle) {
                    ChatFragment chatFragment = ChatFragment.newInstance(anchorBean, isNormalStyle);
                    getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.message_container, chatFragment).addToBackStack(null).commit();
                } else {
                    startActivity(ChatActivity.class, anchorBean);
                }
            }
        });
    }

    @OnClick(R.id.close)
    public void close() {
        getActivity().getSupportFragmentManager().beginTransaction().detach(this).commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
        EaseUI.getInstance().pushActivity(getActivity());
        // register the event listener when enter the foreground
        EMClient.getInstance().chatManager().addMessageListener(this);
    }


    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden)
            refreshList();
    }


    public void refreshList() {
        conversationList.clear();
        conversationList.addAll(loadConversationList());
        conversationListView.refresh();
    }


    @Override
    public void onStop() {
        super.onStop();
        // unregister this event listener when this activity enters the
        // background
        EMClient.getInstance().chatManager().removeMessageListener(this);

        // remove activity from foreground activity list
        EaseUI.getInstance().popActivity(getActivity());
    }

    /**
     * load conversation list
     *
     * @return +
     */
    protected List<EMConversation> loadConversationList() {
        // get all conversations
        Map<String, EMConversation> conversations = EMClient.getInstance().chatManager().getAllConversations();
        if (anchorBean.getAnchorId() != null && !conversations.keySet().contains(anchorBean.getAnchorId())) {
            addAnchorToConversation(conversations);
        }
        List<Pair<Long, EMConversation>> sortList = new ArrayList<Pair<Long, EMConversation>>();
        /**
         * lastMsgTime will change if there is new message during sorting
         * so use synchronized to make sure timestamp of last message won't change.
         */
        synchronized (conversations) {
            for (final EMConversation conversation : conversations.values()) {
                if ((anchorBean.getAnchorId() != null || conversation.getAllMessages().size() != 0) && !conversation.isGroup()) {
                    if (conversation.getAllMessages().size() == 0) {
                        sortList.add(new Pair<Long, EMConversation>(0l, conversation));
                        conversationListView.setConversationListHelper(new EaseConversationList.EaseConversationListHelper() {
                            @Override
                            public String onSetItemSecondaryText(EMMessage lastMessage) {
                                if (conversation.getAllMessages().size() == 0) {
                                    return "Hi，我是主播，快来与我聊天吧";
                                }
                                return EaseCommonUtils.getMessageDigest(lastMessage, getActivity());
                            }
                        });
                    } else {
                        sortList.add(new Pair<Long, EMConversation>(conversation.getLastMessage().getMsgTime(), conversation));
                    }
                }
            }
        }
        try {
            // Internal is TimSort algorithm, has bug
            sortConversationByLastChatTime(sortList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<EMConversation> list = new ArrayList<EMConversation>();
        for (Pair<Long, EMConversation> sortItem : sortList) {
            list.add(sortItem.second);
        }
        return list;
    }

    private void addAnchorToConversation(Map<String, EMConversation> conversations) {
        final EMConversation conversation = EMClient.getInstance().chatManager().getConversation(anchorBean.getAnchorId(),
                EMConversation.EMConversationType.Chat, true);
    }

    /**
     * sort conversations according time stamp of last message
     *
     * @param conversationList
     */
    private void sortConversationByLastChatTime(List<Pair<Long, EMConversation>> conversationList) {
        Collections.sort(conversationList, new Comparator<Pair<Long, EMConversation>>() {
            @Override
            public int compare(final Pair<Long, EMConversation> con1, final Pair<Long, EMConversation> con2) {
                if (anchorBean.getAnchorId() != null) {
                    if (con1.second.getUserName().equals(anchorBean.getAnchorId())) {
                        return -1;
                    } else if (con1.second.getUserName().equals(anchorBean.getAnchorId())) {
                        return 1;
                    } else {
                        return con1.first.compareTo(con2.first);
                    }
                } else {
                    return con1.first.compareTo(con2.first);
                }

            }

        });
    }

    @Override
    public void onMessageReceived(List<EMMessage> list) {
        conversationListView.refresh();
    }

    @Override
    public void onCmdMessageReceived(List<EMMessage> list) {

    }

    @Override
    public void onMessageReadAckReceived(List<EMMessage> list) {

    }

    @Override
    public void onMessageDeliveryAckReceived(List<EMMessage> list) {

    }

    @Override
    public void onMessageChanged(EMMessage emMessage, Object o) {

    }
}
