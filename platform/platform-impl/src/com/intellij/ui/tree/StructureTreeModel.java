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

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

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
  private volatile Comparator<Node> comparator;

  public StructureTreeModel(boolean background) {
    invoker = background
              ? new Invoker.BackgroundThread(this)
              : new Invoker.EDT(this);
  }

  public final void setComparator(Comparator<NodeDescriptor> comparator) {
    if (comparator != null) {
      this.comparator = (node1, node2) -> comparator.compare(node1.descriptor, node2.descriptor);
      invalidate();
    }
    else if (this.comparator != null) {
      this.comparator = null;
      invalidate();
    }
  }

  public void setStructure(AbstractTreeStructure structure) {
    this.structure = structure;
    invalidate();
  }

  @Override
  public void dispose() {
    root.set(null);
    comparator = null;
    structure = null;
  }

  @NotNull
  @Override
  public final Invoker getInvoker() {
    return invoker;
  }

  private boolean isValidThread() {
    if (invoker.isValidThread()) return true;
    LOG.warn("StructureTreeModel is used from unexpected thread");
    return false;
  }

  public final void invalidate() {
    invoker.invokeLaterIfNeeded(() -> {
      root.invalidate();
      Node node = root.get();
      if (node != null) {
        node.invalidate();
        treeStructureChanged(new TreePath(node), null, null);
      }
      else {
        treeStructureChanged(null, null, null);
      }
    });
  }

  @Override
  public final Object getRoot() {
    if (!isValidThread()) return null;
    if (!root.isValid()) {
      root.set(getValidRoot());
    }
    return root.get();
  }

  private Node getNode(Object object) {
    if (!(object instanceof Node) || !isValidThread()) return null;
    Node node = (Node)object;
    if (!node.children.isValid() && node.isNodeAncestor(root.get())) {
      List<Node> newChildren = getValidChildren(node);
      List<Node> oldChildren = node.children.set(newChildren);
      if (oldChildren != null) oldChildren.forEach(child -> child.setParent(null));
      if (newChildren != null) newChildren.forEach(child -> child.setParent(node));
    }
    return node;
  }

  @Override
  public final List<TreeNode> getChildren(Object object) {
    Node node = getNode(object);
    if (node == null || !node.children.isValid()) return null;
    List<Node> list = node.children.get();
    return list == null || list.isEmpty() ? emptyList() : unmodifiableList(list);
  }

  @Override
  public final int getChildCount(Object object) {
    Node node = getNode(object);
    return node == null ? 0 : node.getChildCount();
  }

  @Override
  public final Object getChild(Object object, int index) {
    Node node = getNode(object);
    return node == null ? null : node.getChildAt(index);
  }

  @Override
  public final boolean isLeaf(Object object) {
    Node node = getNode(object);
    return node == null || node.isLeaf();
  }

  @Override
  public final int getIndexOfChild(Object object, Object child) {
    return object instanceof Node ? ((Node)object).getIndexOfChild(child) : -1;
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

    Node node = root.get();
    boolean leaf = structure.isAlwaysLeaf(element);
    if (node == null || leaf == node.getAllowsChildren() || !element.equals(node.descriptor.getElement())) {
      node = new Node(structure.createDescriptor(element, null), leaf);
    }
    node.descriptor.update();
    return node;
  }

  private List<Node> getValidChildren(@NotNull Node node) {
    AbstractTreeStructure structure = this.structure;
    if (structure == null) return null;

    Object parent = node.descriptor.getElement();
    if (parent == null) return null;

    Object[] elements = structure.getChildElements(parent);
    if (elements == null || elements.length == 0) return null;

    HashMap<Object, Node> map = new HashMap<>();
    node.getChildren().forEach(child -> {
      Object element = child.descriptor.getElement();
      if (element != null) map.put(element, child);
    });
    List<Node> list = new ArrayList<>(elements.length);
    for (Object element : elements) {
      if (isValid(element)) {
        Node child = map.get(element);
        boolean leaf = structure.isAlwaysLeaf(element);
        if (child == null || leaf == child.getAllowsChildren()) {
          child = new Node(structure.createDescriptor(element, node.descriptor), leaf);
        }
        child.descriptor.update();
        list.add(child);
      }
    }
    Comparator<Node> comparator = this.comparator;
    if (comparator != null) list.sort(comparator);
    return list;
  }

  private static final class Node extends DefaultMutableTreeNode {
    private final Reference<List<Node>> children = new Reference<>();
    private final NodeDescriptor descriptor;

    private Node(@NotNull NodeDescriptor descriptor, boolean leaf) {
      super(descriptor);
      super.allowsChildren = !leaf;
      this.descriptor = descriptor;
      if (leaf) children.set(null);
    }

    private void invalidate() {
      if (getAllowsChildren()) {
        children.invalidate();
        getChildren().forEach(Node::invalidate);
      }
    }

    @NotNull
    private List<Node> getChildren() {
      List<Node> list = this.children.get();
      return list != null ? list : emptyList();
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
      return children.isValid() && super.isLeaf();
    }

    @Override
    public int getIndex(TreeNode child) {
      return getIndexOfChild(child);
    }

    private int getIndexOfChild(Object child) {
      return child instanceof Node && isNodeChild((Node)child) ? getChildren().indexOf(child) : -1;
    }
  }
}
