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

import com.intellij.util.containers.SortedList;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private List<TreeNode> myCachedAllChildren;
  protected final ValueContainer myValueContainer;
  private volatile boolean myObsolete;

  protected XValueContainerNode(XDebuggerTree tree, final XDebuggerTreeNode parent, @NotNull ValueContainer valueContainer) {
    super(tree, parent, true);
    myValueContainer = valueContainer;
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
  }

  private void loadChildren() {
    if (myValueChildren != null || myMessageChildren != null) return;
    startComputingChildren();
  }

  public void startComputingChildren() {
    myCachedAllChildren = null;
    setMessageNode(createLoadingMessageNode());
    myValueContainer.computeChildren(this);
  }

  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createLoadingMessage(myTree, this);
  }

  @Override
  public void addChildren(@NotNull final XValueChildrenList children, final boolean last) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        if (myValueChildren == null) {
          if (XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues()) {
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
          final int[] ints = getNodesIndices(myMessageChildren);
          final TreeNode[] removed = getChildNodes(ints);
          myCachedAllChildren = null;
          myMessageChildren = null;
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
        setMessageNode(MessageTreeNode.createEllipsisNode(myTree, XValueContainerNode.this, remaining));
      }
    });
  }

  public boolean isObsolete() {
    return myObsolete;
  }

  public void clearChildren() {
    myCachedAllChildren = null;
    myMessageChildren = null;
    myValueChildren = null;
    fireNodeChildrenChanged();
  }

  public void setErrorMessage(final @NotNull String errorMessage) {
    setErrorMessage(errorMessage, null);
  }

  @Override
  public void setErrorMessage(@NotNull final String errorMessage, @Nullable final XDebuggerTreeNodeHyperlink link) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        setMessageNodes(MessageTreeNode.createErrorMessages(myTree, XValueContainerNode.this, errorMessage, link));
      }
    });
  }

  protected void setMessageNode(final MessageTreeNode messageNode) {
    setMessageNodes(Collections.singletonList(messageNode));
  }

  private void setMessageNodes(final List<MessageTreeNode> messages) {
    myCachedAllChildren = null;
    final int[] indices = getNodesIndices(myMessageChildren);
    final TreeNode[] nodes = getChildNodes(indices);
    myMessageChildren = Collections.emptyList();
    fireNodesRemoved(indices, nodes);
    myMessageChildren = messages;
    myCachedAllChildren = null;
    fireNodesInserted(myMessageChildren);
  }

  protected List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myCachedAllChildren == null) {
      myCachedAllChildren = new ArrayList<TreeNode>();
      if (myValueChildren != null) {
        myCachedAllChildren.addAll(myValueChildren);
      }
      if (myMessageChildren != null) {
        myCachedAllChildren.addAll(myMessageChildren);
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
