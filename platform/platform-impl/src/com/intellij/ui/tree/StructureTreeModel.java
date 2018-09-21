// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.AbstractTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.enumeration;
import static java.util.Collections.unmodifiableList;

/**
 * @author Sergey.Malenkov
 */
public class StructureTreeModel extends AbstractTreeModel implements Disposable, InvokerSupplier, ChildrenProvider<TreeNode> {
  private static final Logger LOG = Logger.getInstance(StructureTreeModel.class);
  private final Reference<Node> root = new Reference<>();
  private final Invoker invoker;
  private volatile AbstractTreeStructure structure;
  private volatile Comparator<? super Node> comparator;

  public StructureTreeModel(boolean background) {
    invoker = background
              ? new Invoker.BackgroundThread(this)
              : new Invoker.EDT(this);
  }

  public StructureTreeModel(@NotNull AbstractTreeStructure structure) {
    this.structure = structure;
    invoker = new Invoker.BackgroundThread(this);
  }

  public StructureTreeModel(@NotNull AbstractTreeStructure structure, @NotNull Comparator<? super NodeDescriptor> comparator) {
    this(structure);
    this.comparator = createComparator(comparator);
  }

  private static Comparator<? super Node> createComparator(@NotNull Comparator<? super NodeDescriptor> comparator) {
    return (node1, node2) -> comparator.compare(node1.getDescriptor(), node2.getDescriptor());
  }

  /**
   * @param comparator a comparator to sort tree nodes or {@code null} to disable sorting
   */
  public final void setComparator(@Nullable Comparator<? super NodeDescriptor> comparator) {
    if (disposed) return;
    if (comparator != null) {
      this.comparator = createComparator(comparator);
      invalidate();
    }
    else if (this.comparator != null) {
      this.comparator = null;
      invalidate();
    }
  }

  /**
   * @param structure a structure to build tree model or {@code null} to clear its content
   */
  public void setStructure(@Nullable AbstractTreeStructure structure) {
    if (disposed) return;
    this.structure = structure;
    invalidate();
  }

  @Override
  public void dispose() {
    super.dispose();
    comparator = null;
    structure = null;
    Node node = root.set(null);
    if (node != null) node.dispose();
  }

  @NotNull
  @Override
  public final Invoker getInvoker() {
    return invoker;
  }

  private boolean isValidThread() {
    if (invoker.isValidThread()) return true;
    LOG.warn(new IllegalStateException("StructureTreeModel is used from unexpected thread"));
    return false;
  }

  @NotNull
  public final Promise<?> invalidate() {
    return invoker.runOrInvokeLater(() -> {
      if (disposed) return;
      root.invalidate();
      Node node = root.get();
      LOG.debug("root invalidated: ", node);
      if (node != null) node.invalidate();
      treeStructureChanged(null, null, null);
    });
  }

  public final void invalidate(@NotNull TreePath path, boolean structure) {
    Object component = path.getLastPathComponent();
    if (component instanceof Node) {
      invoker.runOrInvokeLater(() -> {
        Node node = (Node)component;
        if (disposed) return;
        if (isNodeRemoved(node)) return;
        if (isValid(node.getElement())) {
          boolean updated = node.update();
          if (structure) {
            node.invalidate();
            treeStructureChanged(path, null, null);
          }
          else if (updated) {
            treeNodesChanged(path, null, null);
          }
        }
        else {
          LOG.debug("invalid element cannot be updated: ", path);
          TreePath parent = path.getParentPath();
          if (parent != null) {
            invalidate(parent, true);
          }
          else {
            invalidate();
          }
        }
      });
    }
  }

  @Override
  public final TreeNode getRoot() {
    if (disposed || !isValidThread()) return null;
    if (!root.isValid()) {
      Node newRoot = getValidRoot();
      root.set(newRoot);
      LOG.debug("root updated: ", newRoot);
    }
    return root.get();
  }

  private Node getNode(Object object, boolean validateChildren) {
    if (disposed || !(object instanceof Node) || !isValidThread()) return null;
    Node node = (Node)object;
    if (isNodeRemoved(node)) return null;
    if (validateChildren) validateChildren(node);
    return node;
  }

  private void validateChildren(@NotNull Node node) {
    if (!node.children.isValid()) {
      List<Node> newChildren = getValidChildren(node);
      List<Node> oldChildren = node.children.set(newChildren);
      if (oldChildren != null) oldChildren.forEach(child -> child.setParent(null));
      if (newChildren != null) newChildren.forEach(child -> child.setParent(node));
      LOG.debug("children updated: ", node);
    }
  }

  private boolean isNodeRemoved(@NotNull Node node) {
    return !node.isNodeAncestor(root.get());
  }

  @Override
  public final List<TreeNode> getChildren(Object object) {
    Node node = getNode(object, true);
    List<Node> list = node == null ? null : node.children.get();
    if (list == null || list.isEmpty()) return emptyList();
    list.forEach(Node::update);
    return unmodifiableList(list);
  }

  @Override
  public final int getChildCount(Object object) {
    Node node = getNode(object, true);
    return node == null ? 0 : node.getChildCount();
  }

  @Override
  public final Object getChild(Object object, int index) {
    Node node = getNode(object, true);
    return node == null ? null : node.getChildAt(index);
  }

  @Override
  public final boolean isLeaf(Object object) {
    Node node = getNode(object, false);
    return node == null || node.isLeaf(this::validateChildren);
  }

  @Override
  public final int getIndexOfChild(Object object, Object child) {
    return object instanceof Node && child instanceof Node ? ((Node)object).getIndex((TreeNode)child) : -1;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object value) {
  }

  private boolean isValid(Object element) {
    AbstractTreeStructure structure = this.structure;
    if (structure == null) return false;

    if (element == null) return false;
    if (element instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)element;
      if (null == node.getValue()) return false;
    }
    if (element instanceof ValidateableNode) {
      ValidateableNode node = (ValidateableNode)element;
      if (!node.isValid()) return false;
    }
    return structure.isValid(element);
  }

  private Node getValidRoot() {
    AbstractTreeStructure structure = this.structure;
    if (structure == null) return null;

    Object element = structure.getRootElement();
    if (!isValid(element)) return null;

    Node newNode = new Node(structure, element, null); // an exception may be thrown while getting a root
    Node oldNode = root.get();
    if (oldNode != null && oldNode.canReuse(newNode, element)) {
      return oldNode; // reuse old node with possible children
    }
    return newNode;
  }

  @Nullable
  private List<Node> getValidChildren(@NotNull Node node) {
    AbstractTreeStructure structure = this.structure;
    if (structure == null) return null;

    NodeDescriptor descriptor = node.getDescriptor();
    if (descriptor == null) return null;

    Object parent = descriptor.getElement();
    if (!isValid(parent)) return null;

    Object[] elements = structure.getChildElements(parent);
    if (elements.length == 0) return null;

    List<Node> list = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (isValid(element)) {
        list.add(new Node(structure, element, descriptor)); // an exception may be thrown while getting children
      }
    }
    Comparator<? super Node> comparator = this.comparator;
    if (comparator != null) list.sort(comparator); // an exception may be thrown while sorting children

    HashMap<Object, Node> map = new HashMap<>();
    node.getChildren().forEach(child -> {
      Object element = child.getElement();
      if (element != null) map.put(element, child);
    });
    for (int i = 0; i < list.size(); i++) {
      Node newNode = list.get(i);
      Node oldNode = map.get(newNode.getElement());
      if (oldNode != null && oldNode.canReuse(newNode, null)) {
        list.set(i, oldNode); // reuse old node with possible children
      }
    }
    return list;
  }

  private static final class Node extends DefaultMutableTreeNode {
    private final Reference<List<Node>> children = new Reference<>();
    private final LeafState leafState;

    private Node(@NotNull AbstractTreeStructure structure, @NotNull Object element, NodeDescriptor parent) {
      this(structure.createDescriptor(element, parent), structure.getLeafState(element));
    }

    private Node(@NotNull NodeDescriptor descriptor, @NotNull LeafState leafState) {
      super(descriptor, leafState != LeafState.ALWAYS);
      this.leafState = leafState;
      if (leafState == LeafState.ALWAYS) children.set(null); // validate children for leaf node
      update(); // an exception may be thrown while updating
    }

    private void dispose() {
      setParent(null);
      List<Node> list = children.set(null);
      if (list != null) list.forEach(Node::dispose);
    }

    private boolean canReuse(@NotNull Node node, Object element) {
      if (leafState != node.leafState) return false;
      if (element != null && !element.equals(getElement())) return false;
      userObject = node.userObject; // replace old descriptor
      return true;
    }

    private boolean update() {
      NodeDescriptor descriptor = getDescriptor();
      return descriptor != null && descriptor.update();
    }

    private void invalidate() {
      if (leafState != LeafState.ALWAYS) {
        children.invalidate();
        LOG.debug("node invalidated: ", this);
        getChildren().forEach(Node::invalidate);
      }
    }

    @NotNull
    private List<Node> getChildren() {
      List<Node> list = children.get();
      return list != null ? list : emptyList();
    }

    private NodeDescriptor getDescriptor() {
      Object object = getUserObject();
      return object instanceof NodeDescriptor ? (NodeDescriptor)object : null;
    }

    private Object getElement() {
      NodeDescriptor descriptor = getDescriptor();
      return descriptor == null ? null : descriptor.getElement();
    }

    @Override
    public void setUserObject(Object object) {
      throw new UnsupportedOperationException("cannot modify node");
    }

    @Override
    public void setAllowsChildren(boolean value) {
      throw new UnsupportedOperationException("cannot modify node");
    }

    @Override
    public Object clone() {
      throw new UnsupportedOperationException("cannot clone node");
    }

    @Override
    public void insert(MutableTreeNode child, int index) {
      throw new UnsupportedOperationException("cannot insert node");
    }

    @Override
    public void remove(int index) {
      throw new UnsupportedOperationException("cannot remove node");
    }

    @Override
    public Enumeration children() {
      return enumeration(getChildren());
    }

    @Override
    public TreeNode getChildAt(int index) {
      List<Node> list = getChildren();
      return 0 <= index && index < list.size() ? list.get(index) : null;
    }

    @Override
    public int getChildCount() {
      return getChildren().size();
    }

    @Override
    public boolean isLeaf() {
      return isLeaf(null);
    }

    private boolean isLeaf(@Nullable Consumer<Node> validator) {
      // root node should not be a leaf node when it is not visible in a tree
      // javax.swing.tree.VariableHeightLayoutCache.TreeStateNode.expand(boolean)
      if (null == getParent()) return false;
      if (leafState == LeafState.ALWAYS) return true;
      if (leafState == LeafState.NEVER) return false;
      if (leafState == LeafState.DEFAULT && validator != null) validator.accept(this);
      return children.isValid() && super.isLeaf();
    }

    @Override
    public int getIndex(@NotNull TreeNode child) {
      return child instanceof Node && isNodeChild(child) ? getChildren().indexOf(child) : -1;
    }
  }

  /**
   * @deprecated do not use
   */
  @Deprecated
  public final Node getRootImmediately() {
    if (!root.isValid()) {
      root.set(getValidRoot());
    }
    return root.get();
  }

  @Override
  public String toString() {
    return String.valueOf(structure);
  }
}
