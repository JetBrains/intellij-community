/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

/**
 * @author Sergey.Malenkov
 */
public class AsyncTreeModel extends AbstractTreeModel implements Disposable {
  private static final Logger LOG = Logger.getInstance(AsyncTreeModel.class);
  private final Command.Processor processor = new Command.Processor(createForegroundInvoker(), createBackgroundInvoker());
  private final Tree tree = new Tree();
  private final TreeModel model;
  private final TreeModelListener listener = new TreeModelAdapter() {
    protected void process(TreeModelEvent event, EventType type) {
      TreePath path = event.getTreePath();
      if (path == null) {
        // request a new root from model according to the specification
        processor.process(new CmdGetRoot("Reload root", null));
        return;
      }
      Object object = path.getLastPathComponent();
      if (path.getParentPath() == null && type == EventType.StructureChanged) {
        // set a new root object according to the specification
        processor.process(new CmdGetRoot("Update root", object));
        return;
      }
      processor.foreground.invokeLaterIfNeeded(() -> {
        Node node = tree.map.get(object);
        if (node == null || node.children == null) {
          debug("ignore updating of nonexistent node", object);
        }
        else if (type == EventType.NodesChanged) {
          // the object is already updated, so we should not start additional command to update
          AsyncTreeModel.this.treeNodesChanged(event.getTreePath(), event.getChildIndices(), event.getChildren());
        }
        else if (type == EventType.NodesInserted) {
          processor.process(new CmdGetChildren("Insert children", node, false));
        }
        else if (type == EventType.NodesRemoved) {
          processor.process(new CmdGetChildren("Remove children", node, false));
        }
        else {
          processor.process(new CmdGetChildren("Update children", node, true));
        }
      });
    }
  };

  public AsyncTreeModel(TreeModel model) {
    this.model = model;
    this.model.addTreeModelListener(listener);
  }

  @Override
  public void dispose() {
    model.removeTreeModelListener(listener);
  }

  @Override
  public Object getRoot() {
    Object root = tree.root;
    if (root != Tree.ROOT) return root;
    if (!isValidThread()) return null;
    tree.root = null;
    processor.process(new CmdGetRoot("Load root", null));
    return tree.root;
  }

  @Override
  public Object getChild(Object object, int index) {
    Node[] children = getChildren(object);
    return 0 <= index && index < children.length ? children[index].getLastPathComponent() : null;
  }

  @Override
  public int getChildCount(Object object) {
    return getChildren(object).length;
  }

  @Override
  public boolean isLeaf(Object object) {
    Node node = getNode(object);
    return node == null || node.leaf;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object value) {
    processor.background.invokeLaterIfNeeded(() -> model.valueForPathChanged(path, value));
  }

  @Override
  public int getIndexOfChild(Object parent, Object object) {
    return getIndex(getChildren(parent), object);
  }

  @NotNull
  protected Invoker createForegroundInvoker() {
    return new Invoker.EDT(getClass().getName());
  }

  @NotNull
  protected Invoker createBackgroundInvoker() {
    return new Invoker.BackgroundQueue(getClass().getName());
  }

  protected Object createLoadingNode() {
    return null;
  }

  private static void debug(String message, Object object) {
    if (LOG.isDebugEnabled()) LOG.debug(message + ": " + object);
  }

  private static int getIndex(Node[] children, Object object) {
    for (int i = 0; i < children.length; i++) {
      if (object == children[i].getLastPathComponent()) return i;
    }
    return -1;
  }

  private static Node getNode(Node[] children, Object object) {
    for (Node node : children) {
      if (object == node.getLastPathComponent()) return node;
    }
    return null;
  }

  private boolean isValidThread() {
    if (processor.foreground.isValidThread()) return true;
    LOG.warn("AsyncTreeModel is used from unexpected thread");
    return false;
  }

  private Node getNode(Object object) {
    return object == null || !isValidThread() ? null : tree.map.get(object);
  }

  private Node[] getChildren(Object object) {
    Node node = getNode(object);
    if (node == null) return Node.EMPTY;
    Node[] children = node.children;
    if (children != null) return children;

    Object loading = createLoadingNode();
    node.children = loading == null ? Node.EMPTY : new Node[]{new Node(node, loading, true)};
    processor.process(new CmdGetChildren("Load children", node, true));
    return node.children;
  }

  private static final class Tree {
    private final IdentityHashMap<Object, Node> map = new IdentityHashMap<>();
    private static final Object ROOT = new Object();
    private volatile Object root = ROOT;

    private void add(Node node) {
      if (node != null) {
        Node old = map.put(node.getLastPathComponent(), node);
        if (old != null && old != node) {
          LOG.warn("AsyncTreeModel replaces node");
          remove(old.children);
        }
        add(node.children);
      }
    }

    private void add(Node[] children) {
      if (children != null) {
        for (Node child : children) {
          add(child);
        }
      }
    }

    private void remove(Object object) {
      if (object != null) {
        Node node = map.remove(object);
        if (node != null) {
          remove(node.children);
        }
      }
    }

    private void remove(Node[] children) {
      if (children != null) {
        for (Node child : children) {
          remove(child.getLastPathComponent());
        }
      }
    }
  }

  private static final class Node extends TreePath {
    private static final Node[] EMPTY = new Node[0];
    private final TreeModel model;
    private volatile boolean leaf;
    private volatile Node[] children;

    private Node(Object object, TreeModel model) {
      super(object);
      this.model = model;
      this.leaf = model.isLeaf(object);
    }

    private Node(Node parent, Object object, boolean loading) {
      super(parent, object);
      this.model = parent.model;
      this.leaf = loading || model.isLeaf(object);
      if (loading) children = EMPTY;
    }

    private Node[] loadChildren() {
      int count = model.getChildCount(getLastPathComponent());
      if (count <= 0) return EMPTY;

      Node[] children = new Node[count];
      for (int i = 0; i < count; i++) {
        Object child = model.getChild(getLastPathComponent(), i);
        if (child == null) return null;
        children[i] = new Node(this, child, false);
      }
      return children;
    }
  }

  private final class CmdGetRoot implements Command<Node> {
    private final String name;
    private final Object root;

    private CmdGetRoot(String name, Object root) {
      this.name = name;
      this.root = root;
    }

    @Override
    public String toString() {
      return name + ": " + root;
    }

    @Override
    public Node get() {
      Object object = root != null ? root : model.getRoot();
      return object == null ? null : new Node(object, model);
    }

    @Override
    public void accept(Node node) {
      if (node != null) {
        Object object = node.getLastPathComponent();
        if (tree.root == object) {
          Node root = tree.map.get(object);
          if (root == null) {
            debug("ignore updating of changed root", object);
          }
          else {
            root.leaf = node.leaf;
            processor.process(new CmdGetChildren("Reload children", root, true));
          }
        }
        else {
          tree.root = object;
          tree.map.clear();
          tree.map.put(object, node);
          treeStructureChanged(node, null, null);
        }
      }
      else if (tree.root != null) {
        tree.root = null;
        tree.map.clear();
        treeStructureChanged(null, null, null);
      }
    }
  }

  private final class CmdGetChildren implements Command<Node[]> {
    private final String name;
    private final Node node;
    private final boolean deep;

    public CmdGetChildren(String name, Node node, boolean deep) {
      this.name = name;
      this.node = node;
      this.deep = deep;
    }

    @Override
    public String toString() {
      return name + ": " + node.getLastPathComponent();
    }

    @Override
    public Node[] get() {
      return node.loadChildren();
    }

    @Override
    public void accept(Node[] children) {
      Object object = node.getLastPathComponent();
      if (node != tree.map.get(object)) {
        debug("ignore updating of changed node", object);
        return;
      }
      if (children == null) {
        LOG.warn("AsyncTreeModel cannot load children of " + object);
        return;
      }
      Node[] old = node.children;
      node.children = children;
      if (old == null) {
        tree.add(children);
        return;
      }
      if (old.length == 0) {
        if (children.length == 0) return;
        tree.add(children);
        if (listeners.isEmpty()) return;
        int[] indices = new int[children.length];
        Arrays.setAll(indices, i -> i);
        Object[] objects = new Object[children.length];
        Arrays.setAll(objects, i -> children[i].getLastPathComponent());
        treeNodesInserted(node, indices, objects);
        return;
      }
      if (children.length == 0) {
        tree.remove(old);
        if (listeners.isEmpty()) return;
        int[] indices = new int[old.length];
        Arrays.setAll(indices, i -> i);
        Object[] objects = new Object[old.length];
        Arrays.setAll(objects, i -> old[i].getLastPathComponent());
        treeNodesRemoved(node, indices, objects);
        return;
      }
      if (!deep) {
        int[] insert = getIndices(children, old);
        if (insert != null) {
          for (int i : insert) tree.add(children[insert[i]]);
          if (listeners.isEmpty()) return;
          Object[] objects = new Object[insert.length];
          Arrays.setAll(objects, i -> children[insert[i]].getLastPathComponent());
          treeNodesInserted(node, insert, objects);
          return;
        }
        int[] remove = getIndices(old, children);
        if (remove != null) {
          for (int i : remove) tree.remove(old[remove[i]]);
          if (listeners.isEmpty()) return;
          Object[] objects = new Object[remove.length];
          Arrays.setAll(objects, i -> old[remove[i]].getLastPathComponent());
          treeNodesRemoved(node, remove, objects);
          return;
        }
      }
      ArrayList<CmdGetChildren> commands = new ArrayList<>();
      for (Node oldNode : old) {
        Object child = oldNode.getLastPathComponent();
        Node newNode = getNode(children, child);
        if (newNode == null) {
          tree.remove(child);
        }
      }
      for (Node newNode : children) {
        Object child = newNode.getLastPathComponent();
        Node oldNode = getNode(old, child);
        if (oldNode == null) {
          tree.add(newNode);
        }
        else {
          tree.map.put(child, newNode);
          if (oldNode.children != null) {
            newNode.children = oldNode.children;
            commands.add(new CmdGetChildren("Reload children recursively", newNode, true));
          }
        }
      }
      treeStructureChanged(node, null, null);
      for (Command command : commands) {
        processor.process(command);
      }
    }

    private int[] getIndices(Node[] large, Node[] small) {
      int size = large.length - small.length;
      if (size <= 0) return null;

      int i = 0, s = 0;
      int[] indices = new int[size];
      for (int l = 0; l < large.length; l++) {
        if (s == small.length) return null;
        if (large[l].getLastPathComponent() == small[s].getLastPathComponent()) {
          s++;
        }
        else {
          if (i == indices.length) return null;
          indices[i++] = l;
        }
      }
      return indices;
    }
  }
}
