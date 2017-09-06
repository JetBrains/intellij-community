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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.LoadingNode;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Command;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.SmartHashSet;
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
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.jetbrains.concurrency.Promises.rejectedPromise;

/**
 * @author Sergey.Malenkov
 */
public final class AsyncTreeModel extends AbstractTreeModel implements Disposable, Identifiable, Searchable, Navigatable {
  private static final Logger LOG = Logger.getInstance(AsyncTreeModel.class);
  private final Command.Processor processor;
  private final Tree tree = new Tree();
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
      if (object == null) {
        LOG.warn("unsupported path: " + path);
        return;
      }
      if (path.getParentPath() == null && type == EventType.StructureChanged) {
        // set a new root object according to the specification
        processor.process(new CmdGetRoot("Update root", object));
        return;
      }
      onValidThread(() -> {
        Node node = tree.map.get(object);
        if (node == null || node.isLoadingRequired()) {
          LOG.debug("ignore updating of nonexistent node: ", object);
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
    onValidThread(() -> resolve(async, path, entry -> async.setResult(path)));
    return async;
  }

  private Promise<TreePath> resolve(Promise<TreePath> promise) {
    AsyncPromise<TreePath> async = new AsyncPromise<>();
    promise.rejected(onValidThread(async::setError));
    promise.done(onValidThread(path -> resolve(async, path, entry -> async.setResult(path))));
    return async;
  }

  private void resolve(AsyncPromise<TreePath> async, TreePath path, Consumer<Node> consumer) {
    LOG.debug("resolve path: ", path);
    if (path == null) {
      async.setError("path is null");
      return;
    }
    Object object = path.getLastPathComponent();
    if (object == null) {
      async.setError("path is wrong");
      return;
    }
    if (!resolved(path, consumer)) {
      TreePath parent = path.getParentPath();
      if (parent == null) {
        promiseRootEntry().rejected(async::setError).done(node -> {
          if (!resolved(path, consumer)) async.setError("root not found");
        });
      }
      else {
        resolve(async, parent, resolved -> promiseChildren(resolved).rejected(async::setError).done(node -> {
          if (!resolved(path, consumer)) async.setError("path not found");
        }));
      }
    }
  }

  private boolean resolved(@NotNull TreePath path, @NotNull Consumer<Node> consumer) {
    Node node = tree.map.get(path.getLastPathComponent());
    if (node == null || !node.paths.contains(path)) return false;
    LOG.debug("path resolved: ", path);
    consumer.consume(node);
    return true;
  }

  @Override
  public Object getRoot() {
    if (!isValidThread()) return null;
    promiseRootEntry();
    Node node = tree.root;
    return node == null ? null : node.object;
  }

  @Override
  public Object getChild(Object object, int index) {
    List<Node> children = getEntryChildren(object);
    return 0 <= index && index < children.size() ? children.get(index).object : null;
  }

  @Override
  public int getChildCount(Object object) {
    return getEntryChildren(object).size();
  }

  @Override
  public boolean isLeaf(Object object) {
    Node node = getEntry(object);
    return node == null || node.leaf;
  }

  @Override
  public void valueForPathChanged(TreePath path, Object value) {
    processor.background.invokeLaterIfNeeded(() -> model.valueForPathChanged(path, value));
  }

  @Override
  public int getIndexOfChild(Object object, Object child) {
    if (child != null) {
      List<Node> children = getEntryChildren(object);
      for (int i = 0; i < children.size(); i++) {
        if (child.equals(children.get(i).object)) return i;
      }
    }
    return -1;
  }

  /**
   * Starts visiting the tree structure with loading all needed children.
   *
   * @param visitor an object that controls visiting a tree structure
   * @return a promise that will be resolved when visiting is finished
   */
  public Promise<TreePath> visit(@NotNull TreeVisitor visitor) {
    return visit(visitor, true);
  }

  /**
   * Starts visiting the tree structure.
   *
   * @param visitor      an object that controls visiting a tree structure
   * @param allowLoading load all needed children if {@code true}
   * @return a promise that will be resolved when visiting is finished
   */
  public Promise<TreePath> visit(@NotNull TreeVisitor visitor, boolean allowLoading) {
    AbstractTreeWalker<Node> walker = new AbstractTreeWalker<Node>(visitor, node -> node.object) {
      @Override
      protected Collection<Node> getChildren(@NotNull Node node) {
        if (node.leaf || !allowLoading) return node.getChildren();
        promiseChildren(node).done(parent -> setChildren(parent.getChildren())).rejected(this::setError);
        return null;
      }
    };
    onValidThread(() -> {
      if (allowLoading) {
        promiseRootEntry().done(walker::start).rejected(walker::setError);
      }
      else {
        walker.start(tree.root);
      }
    });
    return walker.promise();
  }

  /**
   * @return {@code true} if this model is updating its structure
   */
  public boolean isProcessing() {
    return processor.getTaskCount() > 0;
  }

  private boolean isValidThread() {
    if (processor.foreground.isValidThread()) return true;
    LOG.warn("AsyncTreeModel is used from unexpected thread");
    return false;
  }

  private void onValidThread(Runnable runnable) {
    processor.foreground.invokeLaterIfNeeded(runnable);
  }

  @NotNull
  private <T> Consumer<T> onValidThread(Consumer<T> consumer) {
    return value -> onValidThread(() -> consumer.consume(value));
  }

  @NotNull
  private Promise<Node> promiseRootEntry() {
    return tree.queue.promise(processor, () -> new CmdGetRoot("Load root", null));
  }

  @NotNull
  private Promise<Node> promiseChildren(@NotNull Node node) {
    return node.queue.promise(processor, () -> {
      node.setLoading(!showLoadingNode ? null : new Node(new LoadingNode(), true));
      return new CmdGetChildren("Load children", node, false);
    });
  }

  private Node getEntry(Object object) {
    return object == null || !isValidThread() ? null : tree.map.get(object);
  }

  @NotNull
  private List<Node> getEntryChildren(Object object) {
    Node node = getEntry(object);
    if (node == null) return emptyList();
    promiseChildren(node);
    return node.getChildren();
  }

  private TreeModelEvent createEvent(TreePath path, LinkedHashMap<Object, Integer> map) {
    if (map == null || map.isEmpty()) return new TreeModelEvent(this, path, null, null);
    int i = 0;
    int size = map.size();
    int[] indices = new int[size];
    Object[] children = new Object[size];
    for (Entry<Object, Integer> entry : map.entrySet()) {
      indices[i] = entry.getValue();
      children[i] = entry.getKey();
      i++;
    }
    return new TreeModelEvent(this, path, indices, children);
  }

  private void treeNodesChanged(Node node, LinkedHashMap<Object, Integer> map) {
    if (!listeners.isEmpty()) {
      for (TreePath path : node.paths) {
        listeners.treeNodesChanged(createEvent(path, map));
      }
    }
  }

  private void treeNodesInserted(Node node, LinkedHashMap<Object, Integer> map) {
    if (!listeners.isEmpty()) {
      for (TreePath path : node.paths) {
        listeners.treeNodesInserted(createEvent(path, map));
      }
    }
  }

  private void treeNodesRemoved(Node node, LinkedHashMap<Object, Integer> map) {
    if (!listeners.isEmpty()) {
      for (TreePath path : node.paths) {
        listeners.treeNodesRemoved(createEvent(path, map));
      }
    }
  }

  @NotNull
  private static LinkedHashMap<Object, Integer> getIndices(@NotNull List<Node> children, ToIntFunction<Node> function) {
    LinkedHashMap<Object, Integer> map = new LinkedHashMap<>();
    for (int i = 0; i < children.size(); i++) {
      Node child = children.get(i);
      if (map.containsKey(child.object)) {
        LOG.warn("ignore duplicated " + (function == null ? "old" : "new") + " child at " + i);
      }
      else {
        map.put(child.object, function == null ? i : function.applyAsInt(child));
      }
    }
    return map;
  }

  private static int getIntersectionCount(LinkedHashMap<Object, Integer> indices, Iterable<Object> objects) {
    int count = 0;
    int last = -1;
    for (Object object : objects) {
      Integer index = indices.get(object);
      if (index != null && last < index.intValue()) {
        last = index;
        count++;
      }
    }
    return count;
  }

  private static List<Object> getIntersection(LinkedHashMap<Object, Integer> indices, Iterable<Object> objects) {
    List<Object> list = new ArrayList<>(indices.size());
    int last = -1;
    for (Object object : objects) {
      Integer index = indices.get(object);
      if (index != null && last < index.intValue()) {
        last = index;
        list.add(object);
      }
    }
    return list;
  }

  private static List<Object> getIntersection(LinkedHashMap<Object, Integer> removed, LinkedHashMap<Object, Integer> inserted) {
    if (removed.isEmpty() || inserted.isEmpty()) return emptyList();
    int countOne = getIntersectionCount(removed, inserted.keySet());
    int countTwo = getIntersectionCount(inserted, removed.keySet());
    if (countOne > countTwo) return getIntersection(removed, inserted.keySet());
    if (countTwo > 0) return getIntersection(inserted, removed.keySet());
    return emptyList();
  }

  private abstract class ObsolescentCommand implements Obsolescent, Command<Reference<Node>> {
    final AsyncPromise<Node> promise = new AsyncPromise<>();
    final String name;
    final Object object;
    volatile boolean started;

    ObsolescentCommand(String name, Object object) {
      this.name = name;
      this.object = object;
      LOG.debug("create command: ", this);
    }

    abstract Node getNode(Object object);

    abstract void setNode(Node node);

    boolean isPending() {
      return Promise.State.PENDING == promise.getState();
    }

    @Override
    public String toString() {
      return object == null ? name : name + ": " + object;
    }

    @Override
    public Reference<Node> get() {
      started = true;
      LOG.debug("background command: ", this);
      return Reference.create(() -> getNode(object));
    }

    @Override
    public void accept(Reference<Node> reference) {
      if (isObsolete()) {
        LOG.debug("obsolete command: ", this);
      }
      else if (reference == null) {
        LOG.debug("failed command: ", this);
      }
      else if (reference.isValid()) {
        LOG.debug("foreground command: ", this);
        setNode(reference.get());
      }
      else {
        LOG.debug("restart command: ", this);
        processor.process(this); // restart command
      }
    }
  }

  private final class CmdGetRoot extends ObsolescentCommand {
    private CmdGetRoot(String name, Object object) {
      super(name, object);
      tree.queue.add(this, old -> old.started || old.object != object);
    }

    @Override
    public boolean isObsolete() {
      return this != tree.queue.get();
    }

    @Override
    Node getNode(Object object) {
      if (object == null) object = model.getRoot();
      if (object == null || isObsolete()) return null;
      return new Node(object, model.isLeaf(object));
    }

    @Override
    void setNode(Node loaded) {
      Node root = tree.root;
      if (root == null && loaded == null) {
        LOG.debug("no root");
        tree.queue.done(this, null);
        return;
      }

      if (root != null && loaded != null && root.object.equals(loaded.object)) {
        LOG.debug("same root: ", root.object);
        tree.queue.done(this, root);
        if (root.isLoadingRequired()) return;
        processor.process(new CmdGetChildren("Update root children", root, true));
        return;
      }

      if (root != null) {
        tree.removeMapping(null, root);
      }
      if (!tree.map.isEmpty()) {
        tree.map.values().forEach(node -> {
          node.queue.close();
          LOG.warn("remove staled node: " + node.object);
        });
        tree.map.clear();
      }

      tree.root = loaded;
      if (loaded != null) {
        tree.map.put(loaded.object, loaded);
        TreePath path = new TreePath(loaded.object);
        loaded.insertPath(path);
        treeStructureChanged(path, null, null);
        LOG.debug("new root: ", loaded.object);
        tree.queue.done(this, loaded);
      }
      else {
        treeStructureChanged(null, null, null);
        LOG.debug("root removed");
        tree.queue.done(this, null);
      }
    }
  }

  private final class CmdGetChildren extends ObsolescentCommand {
    private final Node node;
    private volatile boolean deep;

    public CmdGetChildren(String name, Node node, boolean deep) {
      super(name, node.object);
      this.node = node;
      if (deep) this.deep = true;
      node.queue.add(this, old -> {
        if (!deep && old.deep && old.isPending()) this.deep = true;
        return true;
      });
    }

    @Override
    public boolean isObsolete() {
      return this != node.queue.get();
    }

    @Override
    Node getNode(Object object) {
      Node loaded = new Node(object, model.isLeaf(object));
      if (loaded.leaf) return loaded;

      if (model instanceof ChildrenProvider) {
        //noinspection unchecked
        ChildrenProvider<Object> provider = (ChildrenProvider)model;
        List<Object> children = provider.getChildren(object);
        if (children == null) throw new ProcessCanceledException(); // cancel this command
        loaded.children = load(children.size(), index -> children.get(index));
      }
      else {
        loaded.children = load(model.getChildCount(object), index -> model.getChild(object, index));
      }
      return loaded;
    }

    private List<Node> load(int count, IntFunction function) {
      if (count < 0) LOG.warn("illegal child count: " + count);
      if (count <= 0) return emptyList();

      SmartHashSet<Object> set = new SmartHashSet<>(count);
      List<Node> children = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        if (isObsolete()) return null;
        Object child = function.apply(i);
        if (child == null) {
          LOG.warn("ignore null child at " + i);
        }
        else if (!set.add(child)) {
          LOG.warn("ignore duplicated child at " + i);
        }
        else {
          if (isObsolete()) return null;
          children.add(new Node(child, model.isLeaf(child)));
        }
      }
      return children;
    }

    @Override
    void setNode(Node loaded) {
      if (loaded == null || loaded.isLoadingRequired()) {
        LOG.debug("cancelled command: ", this);
        return;
      }
      if (node != tree.map.get(loaded.object)) {
        node.queue.close();
        LOG.warn("ignore removed node: " + node.object);
        return;
      }
      List<Node> oldChildren = node.getChildren();
      List<Node> newChildren = loaded.getChildren();
      if (oldChildren.isEmpty() && newChildren.isEmpty()) {
        node.setLeaf(loaded.leaf);
        treeNodesChanged(node, null);
        LOG.debug("no children: ", node.object);
        node.queue.done(this, node);
        return;
      }

      LinkedHashMap<Object, Integer> removed = getIndices(oldChildren, null);
      if (newChildren.isEmpty()) {
        for (Node child : oldChildren) tree.removeMapping(node, child);
        node.setLeaf(loaded.leaf);
        treeNodesRemoved(node, removed);
        LOG.debug("children removed: ", node.object);
        node.queue.done(this, node);
        return;
      }

      // remove duplicated nodes during indices calculation
      ArrayList<Node> list = new ArrayList<>(newChildren.size());
      SmartHashSet<Object> reload = new SmartHashSet<>();
      LinkedHashMap<Object, Integer> inserted = getIndices(newChildren, child -> {
        Node found = tree.map.get(child.object);
        if (found == null) {
          tree.map.put(child.object, child);
          list.add(child);
        }
        else {
          list.add(found);
          if (!found.isLoadingRequired() && !removed.containsKey(found.object)) {
            reload.add(found.object); // reload reused children if they are inserted
          }
        }
        return list.size() - 1;
      });
      newChildren = list;

      if (oldChildren.isEmpty()) {
        for (Node child : newChildren) child.insertPaths(node);
        node.setChildren(newChildren);
        treeNodesInserted(node, inserted);
        LOG.debug("children inserted: ", node.object);
        node.queue.done(this, node);
        return;
      }

      LinkedHashMap<Object, Integer> contained = new LinkedHashMap<>();
      for (Object object : getIntersection(removed, inserted)) {
        Integer oldIndex = removed.remove(object);
        if (oldIndex == null) {
          LOG.warn("intersection failed");
        }
        Integer newIndex = inserted.remove(object);
        if (newIndex == null) {
          LOG.warn("intersection failed");
        }
        else {
          contained.put(object, newIndex);
          if (deep) reload.add(object);
        }
      }

      for (Node child : oldChildren) {
        if (removed.containsKey(child.object) && !inserted.containsKey(child.object)) {
          tree.removeMapping(node, child);
        }
      }

      for (Node child : newChildren) {
        if (!removed.containsKey(child.object) && inserted.containsKey(child.object)) {
          child.insertPaths(node);
        }
      }

      node.setChildren(newChildren);
      if (!removed.isEmpty()) treeNodesRemoved(node, removed);
      if (!inserted.isEmpty()) treeNodesInserted(node, inserted);
      if (!contained.isEmpty()) treeNodesChanged(node, contained);
      LOG.debug("children changed: ", node.object);
      node.queue.done(this, node);

      if (!reload.isEmpty()) {
        for (Node child : newChildren) {
          if (!child.isLoadingRequired() && reload.contains(child.object)) {
            processor.process(new CmdGetChildren("Update children recursively", child, true));
          }
        }
      }
    }
  }

  private static final class CommandQueue<T extends ObsolescentCommand> {
    private final ArrayDeque<T> deque = new ArrayDeque<>();
    private volatile boolean closed;

    T get() {
      synchronized (deque) {
        return deque.peekFirst();
      }
    }

    @NotNull
    Promise<Node> promise(@NotNull Command.Processor processor, @NotNull Supplier<T> supplier) {
      T command;
      synchronized (deque) {
        command = deque.peekFirst();
        if (command != null) return command.promise;
        command = supplier.get();
      }
      processor.process(command);
      return command.promise;
    }

    void add(@NotNull T command, @NotNull Predicate<T> predicate) {
      synchronized (deque) {
        if (closed) return;
        T old = deque.peekFirst();
        boolean add = old == null || predicate.test(old);
        if (add) deque.addFirst(command);
      }
    }

    void done(T command, Node node) {
      Iterable<AsyncPromise<Node>> promises;
      synchronized (deque) {
        if (closed) return;
        if (!deque.contains(command)) return;
        promises = getPromises(command);
        if (deque.isEmpty()) deque.addLast(command);
      }
      promises.forEach(promise -> promise.setResult(node));
    }

    void close() {
      Iterable<AsyncPromise<Node>> promises;
      synchronized (deque) {
        if (closed) return;
        closed = true;
        if (deque.isEmpty()) return;
        promises = getPromises(null);
      }
      promises.forEach(promise -> promise.setError("cancel loading"));
    }

    private Iterable<AsyncPromise<Node>> getPromises(T command) {
      ArrayList<AsyncPromise<Node>> list = new ArrayList<>();
      while (true) {
        T last = deque.pollLast();
        if (last == null) break;
        if (last.isPending()) list.add(last.promise);
        if (last.equals(command)) break;
      }
      return list;
    }
  }

  private static final class Tree {
    private final CommandQueue<CmdGetRoot> queue = new CommandQueue<>();
    private final HashMap<Object, Node> map = new HashMap<>();
    private volatile Node root;

    private void removeMapping(Node parent, Node child) {
      if (parent != null && parent.loading == child) {
        parent.loading = null;
      }
      else {
        for (Node node : child.getChildren()) {
          //TODO: remove only parent paths
          removeMapping(child, node);
        }
        child.removePaths(parent);
        if (child.paths.isEmpty()) {
          child.queue.close();
          Node node = map.remove(child.object);
          if (node != child) {
            LOG.warn("invalid node: " + child.object);
            if (node != null) map.put(node.object, node);
          }
        }
      }
    }
  }

  private static final class Node {
    private final CommandQueue<CmdGetChildren> queue = new CommandQueue<>();
    private final SmartHashSet<TreePath> paths = new SmartHashSet<>();
    private final Object object;
    private volatile boolean leaf;
    private volatile List<Node> children;
    private volatile Node loading;

    private Node(@NotNull Object object, boolean leaf) {
      this.object = object;
      this.leaf = leaf;
    }

    private void setLeaf(boolean leaf) {
      this.leaf = leaf;
      this.children = leaf ? null : emptyList();
      this.loading = null;
    }

    private void setChildren(List<Node> children) {
      this.leaf = children == null;
      this.children = children;
      this.loading = null;
    }

    private void setLoading(Node loading) {
      this.leaf = false;
      this.children = loading != null ? singletonList(loading) : emptyList();
      this.loading = loading;
    }

    private boolean isLoadingRequired() {
      return !leaf && children == null;
    }

    @NotNull
    private List<Node> getChildren() {
      List<Node> list = children;
      return list != null ? list : emptyList();
    }

    private void insertPath(TreePath path) {
      if (!paths.add(path)) {
        LOG.warn("node is already attached to " + path);
      }
    }

    private void insertPaths(Stream<TreePath> stream) {
      stream.forEach(path -> insertPath(path.pathByAddingChild(object)));
    }

    private void insertPaths(Node parent) {
      if (parent == null) {
        insertPath(new TreePath(object));
      }
      else if (parent.paths.isEmpty()) {
        LOG.warn("insert to invalid parent");
      }
      else {
        insertPaths(parent.paths.stream());
      }
    }

    private void removePath(TreePath path) {
      if (!paths.remove(path)) {
        LOG.warn("node is not attached to " + path);
      }
    }

    private void removePaths(Stream<TreePath> stream) {
      stream.forEach(path -> removePath(path.pathByAddingChild(object)));
    }

    private void removePaths(Node parent) {
      if (parent == null) {
        removePath(new TreePath(object));
      }
      else if (parent.paths.isEmpty()) {
        LOG.warn("remove from invalid parent");
      }
      else {
        removePaths(parent.paths.stream());
      }
    }
  }

  /**
   * @deprecated do not use
   */
  @Deprecated
  public void setRootImmediately(@NotNull Object object) {
    Node node = new Node(object, false);
    node.insertPath(new TreePath(object));
    tree.root = node;
    tree.map.put(object, node);
  }
}
