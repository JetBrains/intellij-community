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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.tree.MapBasedTree.Entry;
import com.intellij.util.concurrency.Command;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

/**
 * @author Sergey.Malenkov
 */
public class AsyncTreeModel extends AbstractTreeModel implements Disposable {
  private static final Logger LOG = Logger.getInstance(AsyncTreeModel.class);
  private final CmdGetRoot rootLoader = new CmdGetRoot("Load root", null);
  private final AtomicBoolean rootLoaded = new AtomicBoolean();
  private final Command.Processor processor;
  private final MapBasedTree<Object, Object> tree = new MapBasedTree<>(true, object -> object);
  private final TreeModel model;
  private final TreeModelListener listener = new TreeModelAdapter() {
    protected void process(TreeModelEvent event, EventType type) {
      TreePath path = event.getTreePath();
      if (path == null) {
        // request a new root from model according to the specification
        processor.process(rootLoader);
        return;
      }
      Object object = path.getLastPathComponent();
      if (path.getParentPath() == null && type == EventType.StructureChanged) {
        // set a new root object according to the specification
        processor.process(new CmdGetRoot("Update root", object));
        return;
      }
      processor.foreground.invokeLaterIfNeeded(() -> {
        Entry<Object> entry = tree.findEntry(object);
        if (entry == null || entry.isLoadingRequired()) {
          LOG.debug("ignore updating of nonexistent node: ", object);
        }
        else if (type == EventType.NodesChanged) {
          // the object is already updated, so we should not start additional command to update
          AsyncTreeModel.this.treeNodesChanged(event.getTreePath(), event.getChildIndices(), event.getChildren());
        }
        else if (type == EventType.NodesInserted) {
          processor.process(new CmdGetChildren("Insert children", entry, false));
        }
        else if (type == EventType.NodesRemoved) {
          processor.process(new CmdGetChildren("Remove children", entry, false));
        }
        else {
          processor.process(new CmdGetChildren("Update children", entry, true));
        }
      });
    }
  };

  public AsyncTreeModel(@NotNull TreeModel model) {
    if (model instanceof Disposable) {
      Disposer.register(this, (Disposable)model);
    }
    Invoker foreground = new Invoker.EDT(this);
    Invoker background = foreground;
    if (model instanceof InvokerSupplier) {
      InvokerSupplier supplier = (InvokerSupplier)model;
      background = supplier.getInvoker();
    }
    this.processor = new Command.Processor(foreground, background);
    this.model = model;
    this.model.addTreeModelListener(listener);
  }

  @Override
  public void dispose() {
    model.removeTreeModelListener(listener);
  }

  @Override
  public Object getRoot() {
    if (!isValidThread()) return null;
    if (!rootLoaded.getAndSet(true)) processor.process(rootLoader);
    Entry<Object> entry = tree.getRootEntry();
    return entry == null ? null : entry.getNode();
  }

  @Override
  public Object getChild(Object object, int index) {
    Entry<Object> entry = getEntry(object, true);
    return entry == null ? null : entry.getChild(index);
  }

  @Override
  public int getChildCount(Object object) {
    Entry<Object> entry = getEntry(object, true);
    return entry == null ? 0 : entry.getChildCount();
  }

  @Override
  public boolean isLeaf(Object object) {
    Entry<Object> entry = getEntry(object, false);
    return entry == null || entry.isLeaf();
  }

  @Override
  public void valueForPathChanged(TreePath path, Object value) {
    processor.background.invokeLaterIfNeeded(() -> model.valueForPathChanged(path, value));
  }

  @Override
  public int getIndexOfChild(Object object, Object child) {
    Entry<Object> entry = getEntry(object, true);
    return entry == null ? -1 : entry.getIndexOf(child);
  }

  protected Object createLoadingNode() {
    return null;
  }

  private boolean isValidThread() {
    if (processor.foreground.isValidThread()) return true;
    LOG.warn("AsyncTreeModel is used from unexpected thread");
    return false;
  }

  private Entry<Object> getEntry(Object object, boolean loadChildren) {
    Entry<Object> entry = object == null || !isValidThread() ? null : tree.findEntry(object);
    if (entry != null && loadChildren && entry.isLoadingRequired()) loadChildren(entry, true);
    return entry;
  }

  private void loadChildren(Entry<Object> entry, boolean insertLoadingNode) {
    String name = insertLoadingNode ? "Load children" : "Reload children";
    if (insertLoadingNode) entry.setLoadingChildren(createLoadingNode());
    processor.process(new CmdGetChildren(name, entry, true));
  }

  private final class CmdGetRoot implements Command<Pair<Object, Boolean>> {
    private final String name;
    private final Object root;

    public CmdGetRoot(String name, Object root) {
      this.name = name;
      this.root = root;
    }

    @Override
    public String toString() {
      return root == null ? name : name + ": " + root;
    }

    @Override
    public Pair<Object, Boolean> get() {
      Object object = root != null ? root : model.getRoot();
      return Pair.create(object, model.isLeaf(object));
    }

    @Override
    public void accept(Pair<Object, Boolean> root) {
      boolean updated = tree.updateRoot(root);
      Entry<Object> entry = tree.getRootEntry();
      if (updated) treeStructureChanged(entry, null, null);
      if (entry != null) loadChildren(entry, entry.isLoadingRequired());
    }
  }

  private final class CmdGetChildren implements Command<List<Pair<Object, Boolean>>> {
    private final String name;
    private final Entry<Object> entry;
    private final boolean deep;

    public CmdGetChildren(String name, Entry<Object> entry, boolean deep) {
      this.name = name;
      this.entry = entry;
      this.deep = deep;
    }

    @Override
    public String toString() {
      return name + ": " + entry.getNode();
    }

    @Override
    public List<Pair<Object, Boolean>> get() {
      Object object = entry.getNode();
      if (model.isLeaf(object)) return null;

      int count = model.getChildCount(object);
      if (count <= 0) return emptyList();

      ArrayList<Pair<Object, Boolean>> children = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        Object child = model.getChild(object, i);
        children.add(Pair.create(child, model.isLeaf(child)));
      }
      return unmodifiableList(children);
    }

    @Override
    public void accept(List<Pair<Object, Boolean>> children) {
      Object object = entry.getNode();
      if (entry != tree.findEntry(object)) {
        LOG.debug("ignore updating of changed node: ", object);
        return;
      }
      boolean isLoadingRequired = entry.isLoadingRequired();
      MapBasedTree.UpdateResult<Object> update = tree.update(entry, children);
      if (isLoadingRequired) return;

      boolean removed = !update.getRemoved().isEmpty();
      boolean inserted = !update.getInserted().isEmpty();
      boolean contained = !update.getContained().isEmpty();
      if (!removed && !inserted && !contained) return;

      if (!deep || !contained) {
        if (!removed && inserted) {
          if (listeners.isEmpty()) return;
          listeners.treeNodesInserted(update.getEvent(AsyncTreeModel.this, entry, update.getInserted()));
          return;
        }
        if (!inserted && removed) {
          if (listeners.isEmpty()) return;
          listeners.treeNodesRemoved(update.getEvent(AsyncTreeModel.this, entry, update.getRemoved()));
          return;
        }
      }
      treeStructureChanged(entry, null, null);
      for (Entry<Object> entry : update.getContained()) {
        if (!entry.isLoadingRequired()) {
          loadChildren(entry, false);
        }
      }
    }
  }
}
