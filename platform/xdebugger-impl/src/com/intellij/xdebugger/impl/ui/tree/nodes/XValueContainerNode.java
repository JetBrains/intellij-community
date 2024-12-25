// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SortedList;
import com.intellij.xdebugger.evaluation.InlineDebuggerHelper;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public abstract class XValueContainerNode<ValueContainer extends XValueContainer> extends XDebuggerTreeNode implements XCompositeNode, TreeNode {
  private List<XValueNodeImpl> myValueChildren;
  private List<MessageTreeNode> myMessageChildren;
  private List<MessageTreeNode> myTemporaryMessageChildren;
  private MessageTreeNode myTemporaryEditorNode;
  private List<XValueContainerNode<?>> myTopNodes;
  private List<XValueContainerNode<?>> myBottomNodes;
  private List<TreeNode> myCachedAllChildren;
  protected final ValueContainer myValueContainer;
  private volatile boolean myObsolete;
  private volatile boolean myAlreadySorted;

  protected XValueContainerNode(XDebuggerTree tree, XDebuggerTreeNode parent, boolean leaf, @NotNull ValueContainer valueContainer) {
    super(tree, parent, leaf);
    myValueContainer = valueContainer;
  }

  private void loadChildren() {
    if (myValueChildren != null || myMessageChildren != null || myTemporaryMessageChildren != null) return;
    startComputingChildren();
  }

  private void prepareForComputingChildren() {
    myCachedAllChildren = null;
    setTemporaryMessageNode(createLoadingMessageNode());
  }

  public void startComputingChildren() {
    prepareForComputingChildren();
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
  public void addChildren(final @NotNull XValueChildrenList children, final boolean last) {
    if (myObsolete) return;
    invokeNodeUpdate(() -> {
      if (myObsolete) return;
      List<XValueContainerNode<?>> newChildren;
      if (children.size() > 0) {
        newChildren = new ArrayList<>(children.size());
        if (myValueChildren == null) {
          myValueChildren = initChildrenList(children.size());
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

      myTopNodes = createNodes(children.getTopValues(), this::createNode, myTopNodes, newChildren);
      myTopNodes = createNodes(children.getTopGroups(), this::createGroupNode, myTopNodes, newChildren);
      myBottomNodes = createNodes(children.getBottomGroups(), this::createGroupNode, myBottomNodes, newChildren);
      myCachedAllChildren = null;
      fireNodesInserted(newChildren);
      if (last && myTemporaryMessageChildren != null) {
        final int[] ints = getNodesIndices(myTemporaryMessageChildren);
        final TreeNode[] removed = myTemporaryMessageChildren.toArray(new TreeNode[0]);
        myCachedAllChildren = null;
        myTemporaryMessageChildren = null;
        fireNodesRemoved(ints, removed);
      }
      myTree.childrenLoaded(this, newChildren, last);
    });
  }

  private List<XValueNodeImpl> initChildrenList(int initialSize) {
    XDebuggerPinToTopManager pinToTopManager = XDebuggerPinToTopManager.Companion.getInstance(myTree.getProject());
    boolean needBaseSorting = !myAlreadySorted && XDebuggerSettingsManager.getInstance().getDataViewSettings().isSortValues();
    boolean isPinToTopSupported = pinToTopManager.isEnabled() && pinToTopManager.isPinToTopSupported(this);

    if (!needBaseSorting && !isPinToTopSupported) {
      return new ArrayList<>(initialSize);
    }

    Comparator<XValueNodeImpl> comparator = needBaseSorting?
                                              isPinToTopSupported?
                                                pinToTopManager.getCompoundComparator()
                                                : XValueNodeImpl.COMPARATOR
                                              : pinToTopManager.getPinToTopComparator();
    return new SortedList<>(comparator);
  }

  private static boolean isUseGetChildrenHack(@NotNull XDebuggerTree tree) {
    return !tree.isUnderRemoteDebug();
  }

  private static @Nullable <T extends XValueContainer> List<XValueContainerNode<?>> createNodes(List<T> groups,
                                                                                                Function<T, XValueContainerNode<?>> createNode,
                                                                                                @Nullable List<XValueContainerNode<?>> prevNodes,
                                                                                                List<? super XValueContainerNode<?>> newChildren) {
    if (groups.isEmpty()) return prevNodes;

    List<XValueContainerNode<?>> nodes = prevNodes != null ? prevNodes : new SmartList<>();
    for (T value : groups) {
      XValueContainerNode<?> node = createNode.apply(value);
      nodes.add(node);
      newChildren.add(node);
    }
    return nodes;
  }

  private XValueContainerNode<?> createNode(XNamedValue value) {
    return new XValueNodeImpl(myTree, this, value.getName(), value);
  }

  private XValueContainerNode<?> createGroupNode(XValueGroup group) {
    return new XValueGroupNodeImpl(myTree, this, group);
  }

  @Override
  public void tooManyChildren(final int remaining) {
    tooManyChildren(remaining, () -> myValueContainer.computeChildren(this));
  }

  @Override
  public void tooManyChildren(int remaining, @NotNull Runnable childrenSupplier) {
    invokeNodeUpdate(() -> setTemporaryMessageNode(
      MessageTreeNode.createEllipsisNode(myTree, this, remaining, () -> {
        myTree.selectNodeOnLoad(n -> n.getParent() == this, n -> isObsolete());
        prepareForComputingChildren();
        childrenSupplier.run();
      })));
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
    myTopNodes = null;
    myBottomNodes = null;
    fireNodeStructureChanged();
  }

  @Override
  public void setErrorMessage(final @NotNull String errorMessage) {
    setErrorMessage(errorMessage, null);
  }

  @Override
  public void setErrorMessage(final @NotNull String errorMessage, final @Nullable XDebuggerTreeNodeHyperlink link) {
    setMessage(errorMessage, XDebuggerUIConstants.ERROR_MESSAGE_ICON, XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES, link);
    invokeNodeUpdate(() -> setMessageNodes(Collections.emptyList(), true)); // clear temporary nodes
  }

  @Override
  public void setMessage(final @NotNull String message,
                         final Icon icon,
                         final @NotNull SimpleTextAttributes attributes,
                         final @Nullable XDebuggerTreeNodeHyperlink link) {
    invokeNodeUpdate(() -> setMessageNodes(MessageTreeNode.createMessages(myTree, this, message, link, icon, attributes), false));
  }

  public void setInfoMessage(@NotNull @Nls String message, @Nullable HyperlinkListener hyperlinkListener) {
    invokeNodeUpdate(() -> setMessageNodes(Collections.singletonList(MessageTreeNode.createInfoMessage(myTree, message, hyperlinkListener)), false));
  }

  /**
   * Adds a temporary message child node which will be removed when children are computed.
   * If this node already has a temporary message, the existing message is replaced by
   * a new one.
   */
  public void setTemporaryMessage(@NotNull MessageTreeNode message) {
    invokeNodeUpdate(() -> setTemporaryMessageNode(message));
  }

  private void setTemporaryMessageNode(final MessageTreeNode messageNode) {
    setMessageNodes(Collections.singletonList(messageNode), true);
  }

  private void setMessageNodes(final List<MessageTreeNode> messages, boolean temporary) {
    myCachedAllChildren = null;
    List<MessageTreeNode> toDelete = temporary ? myTemporaryMessageChildren : myMessageChildren;
    if (toDelete != null) {
      fireNodesRemoved(getNodesIndices(toDelete), toDelete.toArray(new TreeNode[0]));
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

  public @NotNull XDebuggerTreeNode addTemporaryEditorNode(@Nullable Icon icon, @Nullable @Nls String text) {
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

  protected int removeChildNode(List children, XDebuggerTreeNode node) {
    int index = children.indexOf(node);
    if (index != -1) {
      children.remove(node);
      fireNodesRemoved(new int[]{index}, new TreeNode[]{node});
    }
    return index;
  }

  @Override
  public @NotNull @Unmodifiable List<? extends TreeNode> getChildren() {
    loadChildren();

    if (myCachedAllChildren == null) {
      myCachedAllChildren = new ArrayList<>();
      if (myTemporaryEditorNode != null) {
        myCachedAllChildren.add(myTemporaryEditorNode);
      }
      if (myMessageChildren != null) {
        myCachedAllChildren.addAll(myMessageChildren);
      }
      if (myTopNodes != null) {
        myCachedAllChildren.addAll(myTopNodes);
      }
      if (myValueChildren != null) {
        myCachedAllChildren.addAll(myValueChildren);
      }
      if (myBottomNodes != null) {
        myCachedAllChildren.addAll(myBottomNodes);
      }
      if (myTemporaryMessageChildren != null) {
        myCachedAllChildren.addAll(myTemporaryMessageChildren);
      }
    }
    return myCachedAllChildren;
  }

  public @NotNull ValueContainer getValueContainer() {
    return myValueContainer;
  }

  @Override
  public @NotNull @Unmodifiable List<? extends XValueContainerNode<?>> getLoadedChildren() {
    List<? extends XValueContainerNode<?>> empty = Collections.<XValueGroupNodeImpl>emptyList();
    return ContainerUtil.concat(ObjectUtils.notNull(myTopNodes, empty),
                                ObjectUtils.notNull(myValueChildren, empty),
                                ObjectUtils.notNull(myBottomNodes, empty));
  }

  public void setObsolete() {
    myObsolete = true;
  }
}
