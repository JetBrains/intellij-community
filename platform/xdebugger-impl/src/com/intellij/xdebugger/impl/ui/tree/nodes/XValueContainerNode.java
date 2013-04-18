/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  private List<TreeNode> myCachedAllChildren;
  protected final ValueContainer myValueContainer;
  private volatile boolean myObsolete;
  private volatile boolean myAlreadySorted;

  protected XValueContainerNode(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull ValueContainer valueContainer) {
    super(tree, parent, true);
    myValueContainer = valueContainer;
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
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

  public boolean isAlreadySorted() {
    return myAlreadySorted;
  }

  public void setAlreadySorted(boolean alreadySorted) {
    myAlreadySorted = alreadySorted;
  }

  @Override
  public void addChildren(@NotNull final XValueChildrenList children, final boolean last) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        if (myValueChildren == null) {
          if (!isAlreadySorted() && XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues()) {
            myValueChildren = new SortedList<XValueNodeImpl>(XValueNodeImpl.COMPARATOR);
          }
          else {
            myValueChildren = new ArrayList<XValueNodeImpl>();
          }
        }
        List<XValueContainerNode<?>> newChildren = new ArrayList<XValueContainerNode<?>>();
        for (int i = 0; i < children.size(); i++) {
          XValueNodeImpl node = new XValueNodeImpl(myTree, XValueContainerNode.this, children.getName(i), children.getValue(i));
          myValueChildren.add(node);
          newChildren.add(node);
        }
        myCachedAllChildren = null;
        fireNodesInserted(newChildren);
        if (last) {
          final int[] ints = getNodesIndices(myTemporaryMessageChildren);
          final TreeNode[] removed = getChildNodes(ints);
          myCachedAllChildren = null;
          myTemporaryMessageChildren = null;
          fireNodesRemoved(ints, removed);
        }
        myTree.childrenLoaded(XValueContainerNode.this, newChildren, last);
      }
    });
  }

  public void addChildren(final List<? extends XValue> children, final boolean last) {
    final XValueChildrenList list = new XValueChildrenList();
    for (XValue child : children) {
      list.add(null, child);
    }
    addChildren(list, last);
  }

  public void tooManyChildren(final int remaining) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        setTemporaryMessageNode(MessageTreeNode.createEllipsisNode(myTree, XValueContainerNode.this, remaining));
      }
    });
  }

  public boolean isObsolete() {
    return myObsolete;
  }

  public void clearChildren() {
    myCachedAllChildren = null;
    myMessageChildren = null;
    myTemporaryMessageChildren = null;
    myValueChildren = null;
    fireNodeStructureChanged();
  }

  public void setErrorMessage(final @NotNull String errorMessage) {
    setErrorMessage(errorMessage, null);
  }

  @Override
  public void setErrorMessage(@NotNull final String errorMessage, @Nullable final XDebuggerTreeNodeHyperlink link) {
    setMessage(errorMessage, XDebuggerUIConstants.ERROR_MESSAGE_ICON, XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES, link);
  }

  @Override
  public void setMessage(@NotNull final String message,
                         final Icon icon, @NotNull final SimpleTextAttributes attributes, @Nullable final XDebuggerTreeNodeHyperlink link) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        setMessageNodes(MessageTreeNode.createMessages(myTree, XValueContainerNode.this, message, link,
                                                       icon,
                                                       attributes), false);
      }
    });
  }

  private void setTemporaryMessageNode(final MessageTreeNode messageNode) {
    setMessageNodes(Collections.singletonList(messageNode), true);
  }

  private void setMessageNodes(final List<MessageTreeNode> messages, boolean temporary) {
    myCachedAllChildren = null;
    List<MessageTreeNode> allMessageChildren = ContainerUtil.concat(myMessageChildren != null ? myMessageChildren : Collections.<MessageTreeNode>emptyList(),
                                                                    myTemporaryMessageChildren != null ? myTemporaryMessageChildren : Collections.<MessageTreeNode>emptyList());
    final int[] indices = getNodesIndices(allMessageChildren);
    final TreeNode[] nodes = getChildNodes(indices);
    myMessageChildren = null;
    myTemporaryMessageChildren = null;
    fireNodesRemoved(indices, nodes);
    if (!temporary) {
      myMessageChildren = messages;
    }
    else {
      myTemporaryMessageChildren = messages;
    }
    myCachedAllChildren = null;
    fireNodesInserted(messages);
  }

  protected List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myCachedAllChildren == null) {
      myCachedAllChildren = new ArrayList<TreeNode>();
      if (myMessageChildren != null) {
        myCachedAllChildren.addAll(myMessageChildren);
      }
      if (myValueChildren != null) {
        myCachedAllChildren.addAll(myValueChildren);
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

  @Nullable
  public List<XValueNodeImpl> getLoadedChildren() {
    return myValueChildren;
  }

  public void setObsolete() {
    myObsolete = true;
  }
}
