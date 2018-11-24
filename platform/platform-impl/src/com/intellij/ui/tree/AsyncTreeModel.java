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
import com.intellij.ui.LoadingNode;
import com.intellij.ui.tree.MapBasedTree.Entry;
import com.intellij.util.concurrency.Command;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Obsolescent;
import org.jetbrains.concurrency.Promise;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.jetbrains.concurrency.Promises.rejectedPromise;

/**
 * @author Sergey.Malenkov
 */
public final class AsyncTreeModel extends AbstractTreeModel implements Disposable, Identifiable, Searchable, Navigatable {
  private static final Logger LOG = Logger.getInstance(AsyncTreeModel.class);
  private final AtomicReference<AsyncPromise<Entry<Object>>> rootLoader = new AtomicReference<>();
  private final Command.Processor processor;
  private final MapBasedTree<Object, Object> tree = new MapBasedTree<>(true, object -> object);
  private final TreeModel model;
  private final boolean showLoadingNode;
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
    this(model, false);
  }

  public AsyncTreeModel(@NotNull TreeModel model, boolean showLoadingNode) {
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
    this.showLoadingNode = showLoadingNode;
  }

  @Override
  public void dispose() {
    model.removeTreeModelListener(listener);
  }

  @Override
  public Object getUniqueID(@NotNull TreePath path) {
    return model instanceof Identifiable ? ((Identifiable)model).getUniqueID(path) : null;
  }

  @NotNull
  @Override
  public Promise<TreePath> getTreePath(Object object) {
    return model instanceof Searchable ? resolve(((Searchable)model).getTreePath(object)) : rejectedPromise();
  }

  @NotNull
  @Override
  public Promise<TreePath> nextTreePath(@NotNull TreePath path, Object object) {
    return model instanceof Navigatable ? resolve(((Navigatable)model).nextTreePath(path, object)) : rejectedPromise();
  }

  @NotNull
  @Override
  public Promise<TreePath> prevTreePath(@NotNull TreePath path, Object object) {
    return model instanceof Navigatable ? resolve(((Navigatable)model).prevTreePath(path, object)) : rejectedPromise();
  }

  @NotNull
  public Promise<TreePath> resolve(TreePath path) {
    AsyncPromise<TreePath> async = new AsyncPromise<>();
    processor.foreground.invokeLaterIfNeeded(() -> resolve(async, path, entry -> async.setResult(entry)));
    return async;
  }

  private Promise<TreePath> resolve(Promise<TreePath> promise) {
    AsyncPromise<TreePath> async = new AsyncPromise<>();
    promise.rejected(error -> processor.foreground.invokeLaterIfNeeded(() -> async.setError(error)));
    promise.done(result -> processor.foreground.invokeLaterIfNeeded(() -> resolve(async, result, entry -> async.setResult(entry))));
    return async;
  }

  private void resolve(AsyncPromise<TreePath> async, TreePath path, Consumer<Entry<Object>> consumer) {
    if (path == null) {
      async.setError("path is null");
      return;
    }
    Object object = path.getLastPathComponent();
    if (object == null) {
      async.setError("path is wrong");
      return;
    }
    if (!consume(consumer, tree.findEntry(object))) {
      TreePath parent = path.getParentPath();
      if (parent == null) {
        promiseRootEntry().done(entry -> {
          if (entry == null) {
            async.setError("root is null");
          }
          else if (object != entry.getNode()) {
            async.setError("root is wrong");
          }
          else {
            consumer.accept(entry);
          }
        });
      }
      else {
        resolve(async, parent, entry -> processor.process(new Command<List<Pair<Object, Boolean>>>() {
          private CmdGetChildren command = new CmdGetChildren("Sync children", entry, false);

          @Override
          public List<Pair<Object, Boolean>> get() {
            return command.get();
          }

          @Override
          public void accept(List<Pair<Object, Boolean>> children) {
            command.accept(children);
            if (!consume(consumer, tree.findEntry(object))) async.setError("path not found");
          }
        }));
      }
    }
  }

  private static boolean consume(Consumer<Entry<Object>> consumer, Entry<Object> entry) {
    if (entry == null) return false;
    consumer.accept(entry);
    return true;
  }

  @Override
  public Object getRoot() {
    if (!isValidThread()) return null;
    promiseRootEntry();
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

  private boolean isValidThread() {
    if (processor.foreground.isValidThread()) return true;
    LOG.warn("AsyncTreeModel is used from unexpected thread");
    return false;
  }

  private static <T> AsyncPromise<T> create(AtomicReference<AsyncPromise<T>> reference) {
    AsyncPromise<T> newPromise = new AsyncPromise<>();
    AsyncPromise<T> oldPromise = reference.getAndSet(newPromise);
    if (oldPromise != null && Promise.State.PENDING == oldPromise.getState()) newPromise.notify(oldPromise);
    return newPromise;
  }

  private Promise<Entry<Object>> promiseRootEntry() {
    AsyncPromise<Entry<Object>> promise = rootLoader.get();
    if (promise != null) return promise;
    CmdGetRoot command = new CmdGetRoot("Load root", null);
    processor.process(command);
    return command.promise;
  }

  private Entry<Object> getEntry(Object object, boolean loadChildren) {
    Entry<Object> entry = object == null || !isValidThread() ? null : tree.findEntry(object);
    if (entry != null && loadChildren && entry.isLoadingRequired()) loadChildren(entry, true);
    return entry;
  }

  private void loadChildren(Entry<Object> entry, boolean insertLoadingNode) {
    String name = insertLoadingNode ? "Load children" : "Reload children";
    if (insertLoadingNode && showLoadingNode) entry.setLoadingChildren(new LoadingNode());
    processor.process(new CmdGetChildren(name, entry, true));
  }

  private final class CmdGetRoot implements Obsolescent, Command<Pair<Object, Boolean>> {
    private final AsyncPromise<Entry<Object>> promise = create(rootLoader);
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
    public boolean isObsolete() {
      return promise != rootLoader.get();
    }

    @Override
    public Pair<Object, Boolean> get() {
      if (isObsolete()) return null;
      Object object = root != null ? root : model.getRoot();
      if (isObsolete()) return null;
      return Pair.create(object, model.isLeaf(object));
    }

    @Override
    public void accept(Pair<Object, Boolean> root) {
      if (isObsolete()) return;
      boolean updated = tree.updateRoot(root);
      Entry<Object> entry = tree.getRootEntry();
      if (updated) treeStructureChanged(entry, null, null);
      if (entry != null) loadChildren(entry, entry.isLoadingRequired());
      promise.setResult(entry);
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

      if (model instanceof ChildrenProvider) {
        //noinspection unchecked
        ChildrenProvider<Object> provider = (ChildrenProvider)model;
        ArrayList<Pair<Object, Boolean>> children = new ArrayList<>();
        provider.getChildren(object).forEach(child -> add(children, child));
        return unmodifiableList(children);
      }

      int count = model.getChildCount(object);
      if (count <= 0) return emptyList();

      ArrayList<Pair<Object, Boolean>> children = new ArrayList<>(count);
      for (int i = 0; i < count; i++) add(children, model.getChild(object, i));
      return unmodifiableList(children);
    }

    private void add(List<Pair<Object, Boolean>> children, Object child) {
      if (child != null) children.add(Pair.create(child, model.isLeaf(child)));
    }

    @Override
    public void accept(List<Pair<Object, Boolean>> children) {
      Object object = entry.getNode();
      if (entry != tree.findEntry(object)) {
        LOG.debug("ignore updating of changed node: ", object);
        return;
      }
      MapBasedTree.UpdateResult<Object> update = tree.update(entry, children);

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
      if (!listeners.isEmpty()) {
        if (removed) listeners.treeNodesRemoved(update.getEvent(AsyncTreeModel.this, entry, update.getRemoved()));
        if (inserted) listeners.treeNodesInserted(update.getEvent(AsyncTreeModel.this, entry, update.getInserted()));
        if (contained) listeners.treeNodesChanged(update.getEvent(AsyncTreeModel.this, entry, update.getContained()));
      }
      for (Entry<Object> entry : update.getContained()) {
        if (!entry.isLoadingRequired()) {
          loadChildren(entry, false);
        }
      }
    }
  }
}
