// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.ValidateableNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.*;
import static org.jetbrains.concurrency.Promises.rejectedPromise;

public class StructureTreeModel<Structure extends AbstractTreeStructure>
  extends AbstractTreeModel implements Disposable, InvokerSupplier, ChildrenProvider<TreeNode> {

  private static final TreePath ROOT_INVALIDATED = new TreePath(new DefaultMutableTreeNode());
  private static final Logger LOG = Logger.getInstance(StructureTreeModel.class);
  private final Reference<Node> root = new Reference<>();
  private final String description;
  private final Invoker invoker;
  private final Structure structure;
  private volatile Comparator<? super Node> comparator;

  public StructureTreeModel(@NotNull Structure structure, @NotNull Disposable parent) {
    this(structure, null, parent);
  }

  public StructureTreeModel(@NotNull Structure structure,
                            @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                            @NotNull Disposable parent) {
    this(structure, comparator, Invoker.forBackgroundThreadWithReadAction(parent), parent);
  }

  public StructureTreeModel(@NotNull Structure structure,
                            @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                            @NotNull Invoker invoker,
                            @NotNull Disposable parent) {
    this.structure = structure;
    this.description = format(structure.toString());
    this.invoker = invoker;
    this.comparator = comparator == null ? null : wrapToNodeComparator(comparator);
    Disposer.register(parent, this);
  }

  @NotNull
  private static Comparator<? super Node> wrapToNodeComparator(@NotNull Comparator<? super NodeDescriptor<?>> comparator) {
    return (node1, node2) -> comparator.compare(node1.getDescriptor(), node2.getDescriptor());
  }

  /**
   * @param comparator a comparator to sort tree nodes or {@code null} to disable sorting
   */
  public final void setComparator(@Nullable Comparator<? super NodeDescriptor<?>> comparator) {
    if (disposed) return;
    if (comparator != null) {
      this.comparator = wrapToNodeComparator(comparator);
      invalidate();
    }
    else if (this.comparator != null) {
      this.comparator = null;
      invalidate();
    }
  }

  @Override
  public void dispose() {
    comparator = null;
    Node node = root.set(null);
    if (node != null) node.dispose();
    // notify tree to clean up inner structures
    treeStructureChanged(null, null, null);
    super.dispose(); // remove listeners after notification
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

  /**
   * @param function a function to process current structure on a valid thread
   * @return a promise that will be succeed if the specified function returns non-null value
   */
  @NotNull
  private <Result> Promise<Result> onValidThread(@NotNull Function<? super Structure, ? extends Result> function) {
    AsyncPromise<Result> promise = new AsyncPromise<>();
    invoker.invoke(() -> {
      if (!disposed) {
        Result result = function.apply(structure);
        if (result != null) promise.setResult(result);
      }
      if (!promise.isDone()) promise.cancel();
    }).onError(promise::setError);
    return promise;
  }

  /**
   * @param path     a path to the node
   * @param function a function to process corresponding node on a valid thread
   * @return a promise that will be succeed if the specified function returns non-null value
   */
  @NotNull
  private <Result> Promise<Result> onValidThread(@NotNull TreePath path, @NotNull Function<? super Node, ? extends Result> function) {
    Object component = path.getLastPathComponent();
    if (component instanceof Node) {
      Node node = (Node)component;
      return onValidThread(structure -> disposed || isNodeRemoved(node) ? null : function.apply(node));
    }
    return rejectedPromise("unexpected node: " + component);
  }

  /**
   * @param element  an element of the internal tree structure
   * @param function a function to process corresponding node on a valid thread
   * @return a promise that will be succeed if the specified function returns non-null value
   */
  @NotNull
  private <Result> Promise<Result> onValidThread(@NotNull Object element, @NotNull Function<? super Node, ? extends Result> function) {
    return onValidThread(structure -> {
      Node node = root.get();
      if (node == null) return null;
      if (node.matches(element)) return function.apply(node);
      ArrayDeque<Object> stack = new ArrayDeque<>();
      for (Object e = element; e != null; e = structure.getParentElement(e)) stack.push(e);
      if (!node.matches(stack.pop())) return null;
      while (!stack.isEmpty()) {
        node = node.findChild(stack.pop());
        if (node == null) return null;
      }
      return function.apply(node);
    });
  }

  /**
   * Invalidates all nodes and notifies Swing model that a whole tree hierarchy is changed.
   */
  @NotNull
  public final Promise<?> invalidate() {
    return onValidThread(structure -> invalidateInternal(null, true));
  }

  /**
   * Invalidates specified nodes and notifies Swing model that these nodes are changed.
   *
   * @param path      a path to the node to invalidate
   * @param structure {@code true} means that all child nodes must be invalidated;
   *                  {@code false} means that only the node specified by {@code path} must be updated
   * @return a promise that will be succeed if path to invalidate is found
   * @see #invalidate(Object, boolean)
   */
  @NotNull
  public final Promise<TreePath> invalidate(@NotNull TreePath path, boolean structure) {
    return onValidThread(path, node -> invalidateInternal(node, structure));
  }

  /**
   * Invalidates specified nodes and notifies Swing model that these nodes are changed.
   * This method does not bother Swing model if the corresponding nodes have not yet been loaded.
   *
   * @param element   an element of the internal tree structure
   * @param structure {@code true} means that all child nodes must be invalidated;
   *                  {@code false} means that only the node specified by {@code path} must be updated
   * @return a promise that will be succeed if path to invalidate is found
   * @see #invalidate(TreePath, boolean)
   */
  @NotNull
  public final Promise<TreePath> invalidate(@NotNull Object element, boolean structure) {
    return onValidThread(element, node -> invalidateInternal(node, structure));
  }

  @Nullable
  private TreePath invalidateInternal(@Nullable Node node, boolean structure) {
    assert invoker.isValidThread();
    while (node != null && !isValid(node)) {
      if (LOG.isTraceEnabled()) LOG.debug("invalid element cannot be updated: ", node);
      node = (Node)node.getParent();
      structure = true;
    }
    if (node == null) {
      node = root.get();
      if (node != null) node.invalidate();
      root.invalidate();
      if (LOG.isTraceEnabled()) LOG.debug("root invalidated: ", node);
      treeStructureChanged(null, null, null);
      return ROOT_INVALIDATED;
    }
    boolean updated = node.update();
    if (structure) {
      node.invalidate();
      TreePath path = TreePathUtil.pathToTreeNode(node);
      treeStructureChanged(path, null, null);
      return path;
    }
    if (updated) {
      TreePath path = TreePathUtil.pathToTreeNode(node);
      treeNodesChanged(path, null, null);
      return path;
    }
    return null;
  }

  /**
   * Expands a node in the specified tree.
   *
   * @param element  an element of the internal tree structure
   * @param tree     a tree, which nodes should be expanded
   * @param consumer a path consumer called on EDT if path is found and expanded
   */
  public final void expand(@NotNull Object element, @NotNull JTree tree, @NotNull Consumer<? super TreePath> consumer) {
    promiseVisitor(element).onSuccess(visitor -> TreeUtil.expand(tree, visitor, consumer));
  }

  /**
   * Makes visible a node in the specified tree.
   *
   * @param element  an element of the internal tree structure
   * @param tree     a tree, which nodes should be made visible
   * @param consumer a path consumer called on EDT if path is found and made visible
   */
  public final void makeVisible(@NotNull Object element, @NotNull JTree tree, @NotNull Consumer<? super TreePath> consumer) {
    promiseVisitor(element).onSuccess(visitor -> TreeUtil.makeVisible(tree, visitor, consumer));
  }

  /**
   * Selects a node in the specified tree.
   *
   * @param element  an element of the internal tree structure
   * @param tree     a tree, which nodes should be selected
   * @param consumer a path consumer called on EDT if path is found and selected
   */
  public final void select(@NotNull Object element, @NotNull JTree tree, @NotNull Consumer<? super TreePath> consumer) {
    promiseVisitor(element).onSuccess(visitor -> TreeUtil.promiseSelect(tree, visitor).onSuccess(consumer));
  }

  /**
   * Promises to create default visitor to find the specified element.
   *
   * @param element an element of the internal tree structure
   * @return a promise that will be succeed if visitor is created
   * @see TreeUtil#promiseExpand(JTree, TreeVisitor)
   * @see TreeUtil#promiseSelect(JTree, TreeVisitor)
   */
  @NotNull
  public final Promise<TreeVisitor> promiseVisitor(@NotNull Object element) {
    return onValidThread(structure -> new TreeVisitor.ByTreePath<>(
      TreePathUtil.pathToCustomNode(element, structure::getParentElement),
      node -> node instanceof Node ? ((Node)node).getElement() : null));
  }

  @Override
  public final TreeNode getRoot() {
    if (disposed || !isValidThread()) return null;
    if (!root.isValid()) {
      Node newRoot = getValidRoot();
      root.set(newRoot);
      if (LOG.isTraceEnabled()) LOG.debug("root updated: ", newRoot);
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
      if (LOG.isTraceEnabled()) LOG.debug("children updated: ", node);
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
  public final TreeNode getChild(Object object, int index) {
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

  private boolean isValid(@NotNull Node node) {
    return isValid(structure, node.getElement());
  }

  private static boolean isValid(@NotNull AbstractTreeStructure structure, Object element) {
    if (element == null) return false;
    if (element instanceof AbstractTreeNode) {
      AbstractTreeNode<?> node = (AbstractTreeNode<?>)element;
      if (null == node.getValue()) {
        return false;
      }
    }
    if (element instanceof ValidateableNode) {
      ValidateableNode node = (ValidateableNode)element;
      if (!node.isValid()) return false;
    }
    return structure.isValid(element);
  }

  @Nullable
  private Node getValidRoot() {
    Object element = structure.getRootElement();
    if (!isValid(structure, element)) return null;

    Node newNode = new Node(structure, element, null); // an exception may be thrown while getting a root
    Node oldNode = root.get();
    if (oldNode != null && oldNode.canReuse(newNode, element)) {
      return oldNode; // reuse old node with possible children
    }
    return newNode;
  }

  @Nullable
  private List<Node> getValidChildren(@NotNull Node node) {
    NodeDescriptor<?> descriptor = node.getDescriptor();
    if (descriptor == null) return null;

    Object parent = descriptor.getElement();
    if (!isValid(structure, parent)) return null;

    Object[] elements = structure.getChildElements(parent);
    if (elements.length == 0) return null;

    List<Node> list = new ArrayList<>(elements.length);
    for (Object element : elements) {
      ProgressManager.checkCanceled();
      if (isValid(structure, element)) {
        list.add(new Node(structure, element, descriptor)); // an exception may be thrown while getting children
      }
    }
    Comparator<? super Node> comparator = this.comparator;
    if (comparator != null) {
      try {
        list.sort(comparator); // an exception may be thrown while sorting children
      }
      catch (IllegalArgumentException exception) {
        StringBuilder sb = new StringBuilder("unexpected sorting failed in ");
        sb.append(this);
        for (Node next : list) sb.append('\n').append(next);
        LOG.error(sb.toString(), exception);
      }
    }
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

  private static final class Node extends DefaultMutableTreeNode implements LeafState.Supplier {
    private final Reference<List<Node>> children = new Reference<>();
    private final LeafState leafState;
    private final int hashCode;

    private Node(@NotNull AbstractTreeStructure structure, @NotNull Object element, NodeDescriptor<?> parent) {
      this(structure.createDescriptor(element, parent), structure.getLeafState(element), element.hashCode());
    }

    private Node(@NotNull NodeDescriptor descriptor, @NotNull LeafState leafState, int hashCode) {
      super(descriptor, leafState != LeafState.ALWAYS);
      this.leafState = leafState;
      this.hashCode = hashCode;
      if (leafState == LeafState.ALWAYS) children.set(null); // validate children for leaf node
      update(); // an exception may be thrown while updating
    }

    private void dispose() {
      setParent(null);
      List<Node> list = children.set(null);
      if (list != null) list.forEach(Node::dispose);
    }

    private boolean canReuse(@NotNull Node node, Object element) {
      if (leafState != node.leafState || hashCode != node.hashCode) return false;
      if (element != null && !matches(element)) return false;
      userObject = node.userObject; // replace old descriptor
      return true;
    }

    private boolean update() {
      NodeDescriptor<?> descriptor = getDescriptor();
      return descriptor != null && descriptor.update();
    }

    private void invalidate() {
      if (leafState != LeafState.ALWAYS) {
        children.invalidate();
        if (LOG.isTraceEnabled()) LOG.debug("node invalidated: ", this);
        getChildren().forEach(Node::invalidate);
      }
    }

    private boolean matches(@NotNull Object element) {
      return matches(element, element.hashCode());
    }

    private boolean matches(@NotNull Object element, int hashCode) {
      return this.hashCode == hashCode && element.equals(getElement());
    }

    private Node findChild(@NotNull Object element) {
      List<Node> list = children.get();
      if (list != null) {
        if (!list.isEmpty()) {
          int hashCode = element.hashCode();
          Optional<Node> result = list.stream().filter(node -> node.matches(element, hashCode)).findFirst();
          if (result.isPresent()) return result.get(); // found child node that matches given element
        }
        if (LOG.isTraceEnabled()) LOG.debug("node '", getElement(), "' have no child: ", element);
      }
      else {
        if (LOG.isTraceEnabled()) LOG.debug("node '", getElement(), "' have no loaded children");
      }
      return null;
    }

    @NotNull
    private List<Node> getChildren() {
      List<Node> list = children.get();
      return list != null ? list : emptyList();
    }

    private NodeDescriptor<?> getDescriptor() {
      Object object = getUserObject();
      return object instanceof NodeDescriptor ? (NodeDescriptor<?>)object : null;
    }

    private Object getElement() {
      NodeDescriptor<?> descriptor = getDescriptor();
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Enumeration children() {
      return enumeration((Collection)getChildren());
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

    private boolean isLeaf(@Nullable Consumer<? super Node> validator) {
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

    @NotNull
    @Override
    public LeafState getLeafState() {
      return leafState;
    }
  }

  /**
   * @deprecated do not use
   */
  @Deprecated
  public final TreeNode getRootImmediately() {
    if (!root.isValid()) {
      root.set(getValidRoot());
    }
    return root.get();
  }

  /**
   * @return a descriptive name for the instance to help a tree identification
   * @see Invoker#Invoker(String, Disposable, com.intellij.util.ThreeState)
   */
  @Override
  public String toString() {
    return description;
  }

  @NotNull
  private static String format(@NotNull String prefix) {
    for (StackTraceElement element : new Exception().getStackTrace()) {
      if (!StructureTreeModel.class.getName().equals(element.getClassName())) {
        return prefix + " @ " + element.getFileName() + " : " + element.getLineNumber();
      }
    }
    return prefix;
  }
}
