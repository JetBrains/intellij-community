/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import com.intellij.xdebugger.evaluation.InlineDebuggerHelper;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class XValueContainerNode<ValueContainer extends XValueContainer> extends XDebuggerTreeNode implements XCompositeNode, TreeNode {
  private List<XValueNodeImpl> myValueChildren;
  private List<MessageTreeNode> myMessageChildren;
  private List<MessageTreeNode> myTemporaryMessageChildren;
  private MessageTreeNode myTemporaryEditorNode;
  private List<XValueGroupNodeImpl> myTopGroups;
  private List<XValueGroupNodeImpl> myBottomGroups;
  private List<TreeNode> myCachedAllChildren;
  protected final ValueContainer myValueContainer;
  private volatile boolean myObsolete;
  private volatile boolean myAlreadySorted;

  protected XValueContainerNode(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull ValueContainer valueContainer) {
    super(tree, parent, true);
    myValueContainer = valueContainer;
  }

  private void loadChildren() {
    if (myValueChildren != null || myMessageChildren != null || myTemporaryMessageChildren != null) return;
    startComputingChildren();
  }

  public void startComputingChildren() {
    myCachedAllChildren = null;
    setTemporaryMessageNode(createLoadingMessageNode());
    myValueContainer.computeChildren(this);
  }

  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createLoadingMessage(myTree, this);
  }

  @Override
  public void setAlreadySorted(boolean alreadySorted) {
    myAlreadySorted = alreadySorted;
  }

  @Override
  public void addChildren(@NotNull final XValueChildrenList children, final boolean last) {
    if (myObsolete) return;
    invokeNodeUpdate(() -> {
      if (myObsolete) return;
      List<XValueContainerNode<?>> newChildren;
      if (children.size() > 0) {
        newChildren = new ArrayList<>(children.size());
        if (myValueChildren == null) {
          if (!myAlreadySorted && XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues()) {
            myValueChildren = new SortedList<>(XValueNodeImpl.COMPARATOR);
          }
          else {
            myValueChildren = new ArrayList<>(children.size());
          }
        }
        boolean valuesInline = XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowValuesInline();
        InlineDebuggerHelper inlineHelper = getTree().getEditorsProvider().getInlineDebuggerHelper();
        for (int i = 0; i < children.size(); i++) {
          XValueNodeImpl node = new XValueNodeImpl(myTree, this, children.getName(i), children.getValue(i));
          myValueChildren.add(node);
          newChildren.add(node);

          if (valuesInline && inlineHelper.shouldEvaluateChildrenByDefault(node) && isUseGetChildrenHack(myTree)) { //todo[kb]: try to generify this dirty hack
            node.getChildren();
          }
        }
      }
      else {
        newChildren = new SmartList<>();
        if (myValueChildren == null) {
          myValueChildren = new SmartList<>();
        }
      }

      myTopGroups = createGroupNodes(children.getTopGroups(), myTopGroups, newChildren);
      myBottomGroups = createGroupNodes(children.getBottomGroups(), myBottomGroups, newChildren);
      myCachedAllChildren = null;
      fireNodesInserted(newChildren);
      if (last && myTemporaryMessageChildren != null) {
        final int[] ints = getNodesIndices(myTemporaryMessageChildren);
        final TreeNode[] removed = myTemporaryMessageChildren.toArray(new TreeNode[myTemporaryMessageChildren.size()]);
        myCachedAllChildren = null;
        myTemporaryMessageChildren = null;
        fireNodesRemoved(ints, removed);
      }
      myTree.childrenLoaded(this, newChildren, last);
    });
  }

  private static boolean isUseGetChildrenHack(@NotNull XDebuggerTree tree) {
    return !tree.isUnderRemoteDebug();
  }

  @Nullable
  private List<XValueGroupNodeImpl> createGroupNodes(List<XValueGroup> groups,
                                                     @Nullable List<XValueGroupNodeImpl> prevNodes,
                                                     List<XValueContainerNode<?>> newChildren) {
    if (groups.isEmpty()) return prevNodes;

    List<XValueGroupNodeImpl> nodes = prevNodes != null ? prevNodes : new SmartList<>();
    for (XValueGroup group : groups) {
      XValueGroupNodeImpl node = new XValueGroupNodeImpl(myTree, this, group);
      nodes.add(node);
      newChildren.add(node);
    }
    return nodes;
  }

  @Override
  public void tooManyChildren(final int remaining) {
    invokeNodeUpdate(() -> setTemporaryMessageNode(MessageTreeNode.createEllipsisNode(myTree, this, remaining)));
  }

  @Override
  public boolean isObsolete() {
    return myObsolete;
  }

  @Override
  public void clearChildren() {
    myCachedAllChildren = null;
    myMessageChildren = null;
    myTemporaryMessageChildren = null;
    myTemporaryEditorNode = null;
    myValueChildren = null;
    myTopGroups = null;
    myBottomGroups = null;
    fireNodeStructureChanged();
  }

  @Override
  public void setErrorMessage(final @NotNull String errorMessage) {
    setErrorMessage(errorMessage, null);
  }

  @Override
  public void setErrorMessage(@NotNull final String errorMessage, @Nullable final XDebuggerTreeNodeHyperlink link) {
    setMessage(errorMessage, XDebuggerUIConstants.ERROR_MESSAGE_ICON, XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES, link);
    invokeNodeUpdate(() -> setMessageNodes(Collections.emptyList(), true)); // clear temporary nodes
  }

  @Override
  public void setMessage(@NotNull final String message,
                         final Icon icon,
                         @NotNull final SimpleTextAttributes attributes,
                         @Nullable final XDebuggerTreeNodeHyperlink link) {
    invokeNodeUpdate(() -> setMessageNodes(MessageTreeNode.createMessages(myTree, this, message, link, icon, attributes), false));
  }

  public void setInfoMessage(@NotNull String message, @Nullable HyperlinkListener hyperlinkListener) {
    invokeNodeUpdate(() -> setMessageNodes(Collections.singletonList(MessageTreeNode.createInfoMessage(myTree, message, hyperlinkListener)), false));
  }

  private void setTemporaryMessageNode(final MessageTreeNode messageNode) {
    setMessageNodes(Collections.singletonList(messageNode), true);
  }

  private void setMessageNodes(final List<MessageTreeNode> messages, boolean temporary) {
    myCachedAllChildren = null;
    List<MessageTreeNode> toDelete = temporary ? myTemporaryMessageChildren : myMessageChildren;
    if (toDelete != null) {
      fireNodesRemoved(getNodesIndices(toDelete), toDelete.toArray(new TreeNode[toDelete.size()]));
    }
    if (temporary) {
      myTemporaryMessageChildren = messages;
    }
    else {
      myMessageChildren = messages;
    }
    myCachedAllChildren = null;
    fireNodesInserted(messages);
  }

  @NotNull
  public XDebuggerTreeNode addTemporaryEditorNode(@Nullable Icon icon, @Nullable String text) {
    if (isLeaf()) {
      setLeaf(false);
    }
    myTree.expandPath(getPath());
    MessageTreeNode node = new MessageTreeNode(myTree, this, true);
    node.setIcon(icon);
    if (!StringUtil.isEmpty(text)) {
      node.getText().append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    myTemporaryEditorNode = node;
    myCachedAllChildren = null;
    fireNodesInserted(Collections.singleton(node));
    return node;
  }

  public void removeTemporaryEditorNode(XDebuggerTreeNode node) {
    if (myTemporaryEditorNode != null) {
      int index = getIndex(myTemporaryEditorNode);
      myTemporaryEditorNode = null;
      myCachedAllChildren = null;
      fireNodesRemoved(new int[]{index}, new TreeNode[]{node});
    }
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  protected int removeChildNode(List children, XDebuggerTreeNode node) {
    int index = children.indexOf(node);
    if (index != -1) {
      children.remove(node);
      fireNodesRemoved(new int[]{index}, new TreeNode[]{node});
    }
    return index;
  }

  @NotNull
  @Override
  public List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myCachedAllChildren == null) {
      myCachedAllChildren = new ArrayList<>();
      if (myTemporaryEditorNode != null) {
        myCachedAllChildren.add(myTemporaryEditorNode);
      }
      if (myMessageChildren != null) {
        myCachedAllChildren.addAll(myMessageChildren);
      }
      if (myTopGroups != null) {
        myCachedAllChildren.addAll(myTopGroups);
      }
      if (myValueChildren != null) {
        myCachedAllChildren.addAll(myValueChildren);
      }
      if (myBottomGroups != null) {
        myCachedAllChildren.addAll(myBottomGroups);
      }
      if (myTemporaryMessageChildren != null) {
        myCachedAllChildren.addAll(myTemporaryMessageChildren);
      }
    }
    return myCachedAllChildren;
  }

  @NotNull
  public ValueContainer getValueContainer() {
    return myValueContainer;
  }

  @Override
  @NotNull
  public List<? extends XValueContainerNode<?>> getLoadedChildren() {
    List<? extends XValueContainerNode<?>> empty = Collections.<XValueGroupNodeImpl>emptyList();
    return ContainerUtil.concat(ObjectUtils.notNull(myTopGroups, empty),
                                ObjectUtils.notNull(myValueChildren, empty),
                                ObjectUtils.notNull(myBottomGroups, empty));
  }

  public void setObsolete() {
    myObsolete = true;
  }
}
