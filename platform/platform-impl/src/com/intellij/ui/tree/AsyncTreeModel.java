// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.CachedTreePresentation;
import com.intellij.ide.util.treeView.CachedTreePresentationNode;
import com.intellij.ide.util.treeView.CachedTreePresentationSupport;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.treeStructure.BgtAwareTreeModel;
import com.intellij.ui.treeStructure.CachingTreePath;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.tree.AbstractTreeModel;
import com.intellij.util.ui.tree.TreeModelAdapter;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Obsolescent;
import org.jetbrains.concurrency.Promise;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.*;

import static java.util.Collections.emptyList;
import static org.jetbrains.concurrency.Promises.rejectedPromise;
import static org.jetbrains.concurrency.Promises.resolvedPromise;

public final class AsyncTreeModel extends AbstractTreeModel
  implements Searchable, TreeVisitor.LoadingAwareAcceptor, CachedTreePresentationSupport, BgtAwareTreeModel
{
  private static final Logger LOG = Logger.getInstance(AsyncTreeModel.class);
  private final Invoker foreground;
  private final Invoker background;
  private final Tree tree = new Tree();
  private final TreeModel model;
  private final boolean showLoadingNode;
  private final TreeModelListener listener = new TreeModelAdapter() {
    @Override
    protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
      TreePath path = event.getTreePath();
      if (path == null) {
        // request a new root from model according to the specification
        submit(new CmdGetRoot("Reload root", null));
        return;
      }
      Object object = path.getLastPathComponent();
      if (object == null) {
        LOG.warn("unsupported path: " + path);
        return;
      }
      if (path.getParentPath() == null && type == EventType.StructureChanged) {
        // set a new root object according to the specification
        submit(new CmdGetRoot("Update root", object));
        return;
      }
      onValidThread(() -> {
        Node node = tree.map.get(object);
        if (node == null) {
          if (LOG.isTraceEnabled()) LOG.debug("ignore updating of nonexistent node: ", object);
        }
        else if (type == EventType.NodesChanged) {
          // the object is already updated, so we should not start additional command to update
          AsyncTreeModel.this.treeNodesChanged(event.getTreePath(), event.getChildIndices(), event.getChildren());
        }
        else if (node.isLoadingRequired()) {
          // update the object presentation only, if its children are not requested yet
          AsyncTreeModel.this.treeNodesChanged(event.getTreePath(), null, null);
        }
        else if (type == EventType.NodesInserted) {
          submit(new CmdGetChildren("Insert children", node, false));
        }
        else if (type == EventType.NodesRemoved) {
          submit(new CmdGetChildren("Remove children", node, false));
        }
        else {
          submit(new CmdGetChildren("Update children", node, true));
        }
      });
    }
  };

  public AsyncTreeModel(@NotNull TreeModel model, @NotNull Disposable parent) {
    this(model, true, parent);
  }

  public AsyncTreeModel(@NotNull TreeModel model, boolean showLoadingNode, @NotNull Disposable parent) {
    if (model instanceof Disposable) {
      Disposer.register(this, (Disposable)model);
    }
    foreground = Invoker.forEventDispatchThread(this);
    if (model instanceof InvokerSupplier supplier) {
      background = supplier.getInvoker();
    }
    else {
      background = foreground;
    }
    if (background instanceof Invoker.EDT && !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.error(new Throwable("Background invoker shall not be EDT. Please implement InvokerSupplier in your TreeModel"));
    }
    this.model = model;
    this.model.addTreeModelListener(listener);
    this.showLoadingNode = showLoadingNode;
    Disposer.register(parent, this);
  }

  @Override
  public void dispose() {
    EDT.assertIsEdt();
    super.dispose();
    model.removeTreeModelListener(listener);
  }

  @Override
  public @NotNull Promise<TreePath> getTreePath(Object object) {
    if (disposed) {
      return rejectedPromise();
    }
    return resolve(model instanceof Searchable ? ((Searchable)model).getTreePath(object) : null);
  }

  public @NotNull Promise<TreePath> resolve(TreePath path) {
    AsyncPromise<TreePath> async = new AsyncPromise<>();
    onValidThread(() -> resolve(async, path));
    return async;
  }

  private @NotNull Promise<TreePath> resolve(Promise<? extends TreePath> promise) {
    if (promise == null && isValidThread()) {
      return rejectedPromise();
    }
    AsyncPromise<TreePath> async = new AsyncPromise<>();
    if (promise == null) {
      onValidThread(() -> async.setError("rejected"));
    }
    else {
      promise.onError(onValidThread(error -> async.setError(error)));
      promise.onSuccess(onValidThread(path -> resolve(async, path)));
    }
    return async;
  }

  private void resolve(@NotNull AsyncPromise<? super TreePath> async, TreePath path) {
    if (LOG.isTraceEnabled()) LOG.debug("resolve path: ", path);
    if (path == null) {
      async.setError("path is null");
      return;
    }
    Object object = path.getLastPathComponent();
    if (object == null) {
      async.setError("path is wrong");
      return;
    }
    accept(new TreeVisitor.ByTreePath<>(path, o -> o))
      .onProcessed(result -> {
        if (result == null) {
          async.setError("path not found");
          return;
        }
        async.setResult(result);
      });
  }

  @Override
  public Object getRoot() {
    if (disposed) {
      return null;
    }
    onValidThread(() -> promiseRootEntry());
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
    if (node == null) {
      return true;
    }
    if (node.leafState == LeafState.ALWAYS) {
      return true;
    }
    if (node.leafState == LeafState.NEVER) {
      return false;
    }
    if (node.leafState == LeafState.ASYNC && node.children == null) {
      promiseChildren(node);
    }
    List<Node> children = node.children;
    // leaf only if no children were loaded
    return children != null && children.isEmpty();
  }

  @Override
  public void valueForPathChanged(@NotNull TreePath path, Object value) {
    background.invoke(() -> model.valueForPathChanged(path, value));
  }

  @Override
  public int getIndexOfChild(Object object, Object child) {
    if (child != null) {
      List<Node> children = getEntryChildren(object);
      for (int i = 0; i < children.size(); i++) {
        if (child.equals(children.get(i).object)) {
          return i;
        }
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
  @Override
  public @NotNull Promise<TreePath> accept(@NotNull TreeVisitor visitor) {
    return accept(visitor, true);
  }

  /**
   * Starts visiting the tree structure.
   *
   * @param visitor      an object that controls visiting a tree structure
   * @param allowLoading load all needed children if {@code true}
   * @return a promise that will be resolved when visiting is finished
   */
  @Override
  public @NotNull Promise<TreePath> accept(@NotNull TreeVisitor visitor, boolean allowLoading) {
    var walker = createWalker(visitor, allowLoading);
    if (allowLoading) {
      // start visiting on the background thread to ensure that root node is already invalidated
      background.invokeLater(() -> onValidThread(() -> promiseRootEntry().onSuccess(node -> walker.start(node)).onError(
        error -> walker.setError(error))));
    }
    else {
      onValidThread(() -> walker.start(tree.root));
    }
    return walker.promise();
  }

  private @NotNull TreeWalkerBase<Node> createWalker(@NotNull TreeVisitor visitor, boolean allowLoading) {
    if (visitor.visitThread() == TreeVisitor.VisitThread.BGT) {
      return new BgtTreeWalker<>(visitor, background, foreground, node -> node.object) {
        @Override
        protected @Unmodifiable @Nullable Collection<Node> getChildren(@NotNull AsyncTreeModel.Node node) {
          return getChildrenForWalker(node, this, allowLoading);
        }
      };
    }
    return new AbstractTreeWalker<>(visitor, node -> node.object) {
      @Override
      protected @Unmodifiable @Nullable Collection<Node> getChildren(@NotNull Node node) {
        return getChildrenForWalker(node, this, allowLoading);
      }
    };
  }

  private @Unmodifiable @Nullable Collection<@NotNull Node> getChildrenForWalker(@NotNull Node node, TreeWalkerBase<Node> walker, boolean allowLoading) {
    if (node.leafState == LeafState.ALWAYS || !allowLoading) {
      return ContainerUtil.filter(node.getChildren(), n -> n.isLoaded());
    }
    promiseChildren(node)
      .onSuccess(parent -> walker.setChildren(parent.getChildren()))
      .onError(error -> walker.setError(error));
    return null;
  }

  /**
   * @return {@code true} if this model is updating its structure
   */
  public boolean isProcessing() {
    if (foreground.getTaskCount() > 0) {
      return true;
    }
    if (background.getTaskCount() > 0) {
      return true;
    }
    Command command = tree.queue.get();
    return command != null && command.isPending();
  }

  /**
   * Lets the specified command to produce a value on the background thread
   * and to accept the resulting value on the foreground thread.
   */
  private void submit(@NotNull Command command) {
    computeTreeDataOnBgt(command).thenAsync(value -> applyToUiTree(command, value));
  }

  private @NotNull Promise<Node> computeTreeDataOnBgt(@NotNull Command command) {
    if (command.canRunAsync()) {
      return background.computeAsync(() -> command.computeAsync());
    }
    return background.compute(() -> command.computeNode());
  }

  private @NotNull Promise<Void> applyToUiTree(@NotNull Command command, @Nullable Node value) {
    return foreground.compute(() -> {
      command.applyToUiTree(value);
      return null;
    });
  }

  private boolean isValidThread() {
    if (foreground.isValidThread()) {
      return true;
    }
    LOG.warn(new IllegalStateException("AsyncTreeModel is used from unexpected thread"));
    return false;
  }

  public void onValidThread(@NotNull Runnable runnable) {
    foreground.invoke(runnable);
  }

  private @NotNull <T> Consumer<T> onValidThread(@NotNull Consumer<? super T> consumer) {
    return value -> onValidThread(() -> consumer.accept(value));
  }

  private @NotNull Promise<Node> promiseRootEntry() {
    if (disposed) {
      return rejectedPromise();
    }
    return tree.queue.promise(command -> submit(command), () -> new CmdGetRoot("Load root", null));
  }

  private @NotNull Promise<Node> promiseChildren(@NotNull Node node) {
    if (disposed) {
      return rejectedPromise();
    }
    return node.queue.promise(command -> submit(command), () -> {
      var cachedNodes = getChildrenFromCachedPresentation(node);
      if (cachedNodes != null) {
        node.setChildren(cachedNodes);
      }
      else if (showLoadingNode) {
        node.setLoading(new Node(new LoadingNode(), LeafState.ALWAYS));
      }
      else {
        node.setLoading(null);
      }
      return new CmdGetChildren("Load children", node, false);
    });
  }

  private @Unmodifiable @Nullable List<Node> getChildrenFromCachedPresentation(@NotNull AsyncTreeModel.Node parent) {
    var cachedPresentation = tree.cachedPresentation;
    if (cachedPresentation == null) {
      return null;
    }
    for (TreePath parentPath : parent.paths) {
      var cachedChildren = cachedPresentation.getChildren(parentPath.getLastPathComponent());
      if (cachedChildren != null) {
        return ContainerUtil.map(cachedChildren, child -> toNode(parent, cachedPresentation, parentPath.pathByAddingChild(child)));
      }
    }
    return null;
  }

  private @NotNull Node toNode(
    @Nullable Node parent,
    @NotNull CachedTreePresentation cachedPresentation,
    @NotNull TreePath nodePath
  ) {
    var object = nodePath.getLastPathComponent();
    var result = new Node(object, cachedPresentation.isLeaf(object) ? LeafState.ALWAYS : LeafState.NEVER);
    if (parent == null) {
      result.paths.add(new CachingTreePath(object));
    }
    else {
      for (TreePath path : parent.paths) {
        result.paths.add(path.pathByAddingChild(object));
      }
    }
    tree.map.put(object, result);
    var resultChildren = cachedPresentation.getChildren(nodePath.getLastPathComponent());
    if (resultChildren != null) {
      result.children = ContainerUtil.map(resultChildren, child -> toNode(result, cachedPresentation, nodePath.pathByAddingChild(child)));
    }
    return result;
  }

  private Node getEntry(Object object) {
    return disposed || object == null || !isValidThread() ? null : tree.map.get(object);
  }

  private @NotNull List<Node> getEntryChildren(Object object) {
    Node node = getEntry(object);
    if (node == null) {
      return emptyList();
    }
    if (node.isLoadingRequired()) {
      promiseChildren(node);
    }
    return node.getChildren();
  }

  private @NotNull TreeModelEvent createEvent(@NotNull TreePath path, @Nullable Object2IntMap<Object> map) {
    if (map == null || map.isEmpty()) {
      return new TreeModelEvent(this, path, null, null);
    }
    int i = 0;
    int size = map.size();
    int[] indices = new int[size];
    Object[] children = new Object[size];
    for (Object2IntMap.Entry<Object> entry : map.object2IntEntrySet()) {
      indices[i] = entry.getIntValue();
      children[i] = entry.getKey();
      i++;
    }
    return new TreeModelEvent(this, path, indices, children);
  }

  private void treeNodesChanged(@NotNull Node node, @Nullable Object2IntMap<Object> map) {
    if (!listeners.isEmpty()) {
      for (TreePath path : node.paths) {
        listeners.treeNodesChanged(createEvent(path, map));
      }
    }
  }

  private void treeNodesInserted(@NotNull Node node, @NotNull Object2IntMap<Object> map) {
    if (!listeners.isEmpty()) {
      for (TreePath path : node.paths) {
        listeners.treeNodesInserted(createEvent(path, map));
      }
    }
  }

  private void treeNodesRemoved(@NotNull Node node, @NotNull Object2IntMap<Object> map) {
    if (!listeners.isEmpty()) {
      for (TreePath path : node.paths) {
        listeners.treeNodesRemoved(createEvent(path, map));
      }
    }
  }

  private static @NotNull Object2IntMap<Object> createObject2IntLinkedMap(int size) {
    Object2IntLinkedOpenHashMap<Object> map = new Object2IntLinkedOpenHashMap<>(size);
    map.defaultReturnValue(-1);
    return map;
  }
  private static @NotNull Object2IntMap<Object> getIndices(@NotNull List<? extends Node> children, @Nullable ToIntFunction<? super Node> function) {
    Object2IntMap<Object> map = createObject2IntLinkedMap(children.size());
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

  private static int getIntersectionCount(@NotNull Object2IntMap<Object> indices, @NotNull Iterable<Object> objects) {
    int count = 0;
    int last = -1;
    for (Object object : objects) {
      int index = indices.getOrDefault(object, -1);
      if (index != -1 && last < index) {
        last = index;
        count++;
      }
    }
    return count;
  }

  private static @NotNull List<Object> getIntersection(@NotNull Object2IntMap<Object> indices, @NotNull Iterable<Object> objects) {
    List<Object> list = new ArrayList<>(indices.size());
    int last = -1;
    for (Object object : objects) {
      int index = indices.getOrDefault(object, -1);
      if (index != -1 && last < index) {
        last = index;
        list.add(object);
      }
    }
    return list;
  }

  private static @NotNull List<Object> getIntersection(@NotNull Object2IntMap<Object> removed, @NotNull Object2IntMap<Object> inserted) {
    if (removed.isEmpty() || inserted.isEmpty()) {
      return emptyList();
    }
    int countOne = getIntersectionCount(removed, inserted.keySet());
    int countTwo = getIntersectionCount(inserted, removed.keySet());
    if (countOne > countTwo) {
      return getIntersection(removed, inserted.keySet());
    }
    if (countTwo > 0) {
      return getIntersection(inserted, removed.keySet());
    }
    return emptyList();
  }

  private abstract static class Command implements Obsolescent {
    final AsyncPromise<Node> promise = new AsyncPromise<>();
    final String name;
    final Object object;
    volatile boolean started;

    Command(@NotNull @NonNls String name, Object object) {
      this.name = name;
      this.object = object;
      if (LOG.isTraceEnabled()) LOG.debug("create command: ", this);
    }

    boolean canRunAsync() {
      return false;
    }

    @NotNull
    Promise<Node> computeAsync() {
      started = true;
      if (isObsolete()) {
        if (LOG.isTraceEnabled()) LOG.debug("obsolete command: ", this);
        return resolvedPromise(null);
      }
      else {
        if (LOG.isTraceEnabled()) LOG.debug("background async command: ", this);
        return computeAsync(object);
      }
    }

    @NotNull
    Promise<Node> computeAsync(Object object) {
      Node node = computeNode(object);
      return resolvedPromise(node);
    }

    abstract Node computeNode(Object object);

    abstract void applyNodeToUiTree(Node node);

    boolean isPending() {
      return Promise.State.PENDING == promise.getState();
    }

    @Override
    public String toString() {
      return object == null ? name : name + ": " + object;
    }

    public Node computeNode() {
      started = true;
      if (isObsolete()) {
        if (LOG.isTraceEnabled()) LOG.debug("obsolete command: ", this);
        return null;
      }
      else {
        if (LOG.isTraceEnabled()) LOG.debug("background command: ", this);
        return computeNode(object);
      }
    }

    public void applyToUiTree(Node node) {
      if (isObsolete()) {
        if (LOG.isTraceEnabled()) LOG.debug("obsolete command: ", this);
      }
      else {
        if (LOG.isTraceEnabled()) LOG.debug("foreground command: ", this);
        applyNodeToUiTree(node);
      }
    }
  }

  private final class CmdGetRoot extends Command {
    private CmdGetRoot(@NotNull @NonNls String name, Object object) {
      super(name, object);
      tree.queue.add(this, old -> old.started || old.object != object);
    }

    @Override
    public boolean isObsolete() {
      return disposed || this != tree.queue.get();
    }

    @Override
    Node computeNode(Object object) {
      if (object == null) {
        object = model.getRoot();
      }
      if (object == null || isObsolete()) {
        return null;
      }
      return new Node(object, LeafState.get(object, model));
    }

    @Override
    void applyNodeToUiTree(Node loaded) {
      Node root = tree.root;
      if (root == null && loaded == null) {
        if (LOG.isTraceEnabled()) LOG.debug("no root");
        tree.queue.done(this, null);
        return;
      }

      if (root != null && loaded != null && root.object.equals(loaded.object)) {
        tree.fixEqualButNotSame(root, loaded.object);
        if (LOG.isTraceEnabled()) LOG.debug("same root: ", root.object);
        if (!root.isLoadingRequired()) {
          submit(new CmdGetChildren("Update root children", root, true));
        }
        tree.queue.done(this, root);
        return;
      }

      if (root != null) {
        root.removeMapping(null, tree);
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
        TreePath path = new CachingTreePath(loaded.object);
        loaded.insertPath(path);
        if (tree.cachedPresentation != null) {
          tree.cachedPresentation.rootLoaded(loaded.object);
        }
        treeStructureChanged(path, null, null);
        if (LOG.isTraceEnabled()) LOG.debug("new root: ", loaded.object);
        tree.queue.done(this, loaded);
      }
      else {
        treeStructureChanged(null, null, null);
        if (LOG.isTraceEnabled()) LOG.debug("root removed");
        tree.queue.done(this, null);
      }
    }
  }

  private final class CmdGetChildren extends Command {
    private final Node node;
    private volatile boolean deep;

    CmdGetChildren(@NotNull @NonNls String name, @NotNull Node node, boolean deep) {
      super(name, node.object);
      this.node = node;
      if (deep) {
        this.deep = true;
      }
      node.queue.add(this, old -> {
        if (!deep && old.deep && old.isPending()) {
          this.deep = true;
        }
        return true;
      });
    }

    @Override
    public boolean isObsolete() {
      return disposed || this != node.queue.get();
    }

    @Override
    boolean canRunAsync() {
      return model instanceof AsyncChildrenProvider<?>;
    }

    @Override
    @NotNull Promise<Node> computeAsync(Object object) {
      Node loaded = new Node(object, LeafState.get(object, model));
      if (loaded.leafState == LeafState.ALWAYS || isObsolete()) {
        return resolvedPromise(loaded);
      }

      if (model instanceof AsyncChildrenProvider<?> provider) {
        Promise<? extends List<?>> childrenPromise = provider.getChildrenAsync(object);
        return childrenPromise.then(children -> {
          loaded.children = load(children);
          return loaded;
        });
      }
      return super.computeAsync(object);
    }

    @Override
    @NotNull
    Node computeNode(Object object) {
      Node loaded = new Node(object, LeafState.get(object, model));
      if (loaded.leafState == LeafState.ALWAYS || isObsolete()) {
        return loaded;
      }

      if (model instanceof ChildrenProvider<?> provider) {
        loaded.children = load(provider.getChildren(object));
      }
      else {
        loaded.children = load(model.getChildCount(object), index -> model.getChild(object, index));
      }
      return loaded;
    }

    private @Nullable List<Node> load(@Nullable List<?> children) {
      if (children == null) {
        throw new ProcessCanceledException(); // cancel this command
      }
      return load(children.size(), index -> children.get(index));
    }

    private @Nullable List<Node> load(int count, @NotNull IntFunction<?> childGetter) {
      if (count < 0) {
        LOG.warn("illegal child count: " + count);
      }
      if (count <= 0) {
        return emptyList();
      }

      Set<Object> set = count == 1 ? new SmartHashSet<>() : new HashSet<>(count);
      List<Node> children = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        ProgressManager.checkCanceled();
        if (isObsolete()) {
          return null;
        }
        Object child = childGetter.apply(i);
        if (child == null) {
          LOG.warn("ignore null child at " + i);
        }
        else if (!set.add(child)) {
          LOG.warn("ignore duplicated child at " + i + ": " + child);
        }
        else {
          if (isObsolete()) {
            return null;
          }
          children.add(new Node(child, LeafState.get(child, model)));
        }
      }
      return children;
    }

    @Override
    void applyNodeToUiTree(Node loaded) {
      if (loaded == null || loaded.isLoadingRequired()) {
        if (LOG.isTraceEnabled()) LOG.debug("cancelled command: ", this);
        return;
      }
      if (node != tree.map.get(loaded.object)) {
        node.queue.close();
        LOG.warn("ignore removed node: " + node.object);
        return;
      }
      List<Node> oldChildren = node.getChildren();
      List<Node> newChildren = loaded.getChildren();
      if (tree.cachedPresentation != null) {
        tree.cachedPresentation.childrenLoaded(node.object, ContainerUtil.map(newChildren, child -> child.object));
      }
      if (oldChildren.isEmpty() && newChildren.isEmpty()) {
        node.setLeafState(loaded.leafState);
        treeNodesChanged(node, null);
        if (LOG.isTraceEnabled()) LOG.debug("no children: ", node.object);
        node.queue.done(this, node);
        return;
      }

      Object2IntMap<Object> removed = getIndices(oldChildren, null);
      if (newChildren.isEmpty()) {
        oldChildren.forEach(child -> child.removeMapping(node, tree));
        node.setLeafState(loaded.leafState);
        treeNodesRemoved(node, removed);
        if (LOG.isTraceEnabled()) LOG.debug("children removed: ", node.object);
        node.queue.done(this, node);
        return;
      }

      // remove duplicated nodes during indices calculation
      List<Node> list = new ArrayList<>(newChildren.size());
      Set<Object> reload = new SmartHashSet<>();
      Object2IntMap<Object> inserted = getIndices(newChildren, child -> {
        Node found = tree.map.get(child.object);
        if (found == null) {
          tree.map.put(child.object, child);
          list.add(child);
        }
        else {
          tree.fixEqualButNotSame(found, child.object);
          list.add(found);
          if (found.leafState == LeafState.ALWAYS) {
            if (child.leafState != LeafState.ALWAYS) {
              found.setLeafState(child.leafState); // mark existing leaf node as not a leaf
              reload.add(found.object); // and request to load its children
            }
          }
          else if (child.leafState == LeafState.ALWAYS || !found.isLoadingRequired() && (deep || !removed.containsKey(found.object))) {
            reload.add(found.object); // request to load children of existing node
          }
        }
        return list.size() - 1;
      });
      newChildren = list;

      if (oldChildren.isEmpty()) {
        newChildren.forEach(child -> child.insertMapping(node));
        node.setChildren(newChildren);
        treeNodesInserted(node, inserted);
        if (LOG.isTraceEnabled()) LOG.debug("children inserted: ", node.object);
        node.queue.done(this, node);
        return;
      }

      List<Object> intersection = getIntersection(removed, inserted);
      Object2IntMap<Object> contained = createObject2IntLinkedMap(intersection.size());
      for (Object object : intersection) {
        int oldIndex = removed.removeInt(object);
        if (oldIndex == -1) {
          LOG.warn("intersection failed");
        }
        int newIndex = inserted.removeInt(object);
        if (newIndex == -1) {
          LOG.warn("intersection failed");
        }
        else {
          contained.put(object, newIndex);
        }
      }

      for (Node child : newChildren) {
        if (!removed.containsKey(child.object) && inserted.containsKey(child.object)) {
          child.insertMapping(node);
        }
      }

      for (Node child : oldChildren) {
        if (removed.containsKey(child.object) && !inserted.containsKey(child.object)) {
          child.removeMapping(node, tree);
        }
      }

      node.setChildren(newChildren);
      if (!removed.isEmpty()) {
        treeNodesRemoved(node, removed);
      }
      if (!inserted.isEmpty()) {
        treeNodesInserted(node, inserted);
      }
      if (!contained.isEmpty()) {
        treeNodesChanged(node, contained);
      }
      if (removed.isEmpty() && inserted.isEmpty() && contained.isEmpty()) {
        treeNodesChanged(node, null);
      }
      if (LOG.isTraceEnabled()) {
        LOG.debug("children changed: ", node.object);
      }

      if (!reload.isEmpty()) {
        for (Node child : newChildren) {
          if (reload.contains(child.object)) {
            submit(new CmdGetChildren("Update children recursively", child, true));
          }
        }
      }
      node.queue.done(this, node);
    }
  }

  private static final class CommandQueue<T extends Command> {
    private final Deque<T> deque = new LinkedList<>();
    private volatile boolean closed;

    Command get() {
      synchronized (deque) {
        return deque.peekFirst();
      }
    }

    @NotNull
    Promise<Node> promise(@NotNull Consumer<? super Command> submitter, @NotNull Supplier<? extends Command> supplier) {
      Command command;
      synchronized (deque) {
        command = deque.peekFirst();
        if (command != null) {
          return command.promise;
        }
        command = supplier.get();
      }
      submitter.accept(command);
      return command.promise;
    }

    void add(@NotNull T command, @NotNull Predicate<? super T> predicate) {
      synchronized (deque) {
        if (closed) {
          return;
        }
        T old = deque.peekFirst();
        boolean add = old == null || predicate.test(old);
        if (add) {
          deque.addFirst(command);
        }
      }
    }

    void done(@NotNull T command, Node node) {
      Iterable<AsyncPromise<Node>> promises;
      synchronized (deque) {
        if (closed) {
          return;
        }
        if (!deque.contains(command)) {
          return;
        }
        promises = getPromises(command);
        if (deque.isEmpty()) {
          deque.addLast(command);
        }
      }
      promises.forEach(promise -> promise.setResult(node));
    }

    void close() {
      Iterable<AsyncPromise<Node>> promises;
      synchronized (deque) {
        if (closed) {
          return;
        }
        closed = true;
        if (deque.isEmpty()) {
          return;
        }
        promises = getPromises(null);
      }
      promises.forEach(promise -> promise.setError("cancel loading"));
    }

    private @NotNull Iterable<AsyncPromise<Node>> getPromises(@Nullable Command command) {
      List<AsyncPromise<Node>> list = new ArrayList<>();
      while (true) {
        T last = deque.pollLast();
        if (last == null) {
          break;
        }
        if (last.isPending()) {
          list.add(last.promise);
        }
        if (last.equals(command)) {
          break;
        }
      }
      return list;
    }
  }

  private static final class Tree {
    private final CommandQueue<CmdGetRoot> queue = new CommandQueue<>();
    private final Map<Object, Node> map = new HashMap<>();
    private volatile Node root;
    private @Nullable CachedTreePresentation cachedPresentation;

    private void removeEmpty(@NotNull Node child) {
      child.forEachChildExceptLoading(node -> removeEmpty(node));
      if (child.paths.isEmpty()) {
        child.queue.close();
        Node node = map.remove(child.object);
        if (node != child) {
          LOG.warn("invalid node: " + child.object);
          if (node != null) map.put(node.object, node);
        }
      }
    }

    private void fixEqualButNotSame(@NotNull Node node, @NotNull Object object) {
      if (object == node.object) {
        return;
      }
      // always use new instance of user's object, because
      // some trees provide equal nodes with different behavior
      map.remove(node.object);
      node.updatePaths(node.object, object);
      node.object = object;
      map.put(object, node); // update key
    }
  }

  private static final class Node {
    private final CommandQueue<CmdGetChildren> queue = new CommandQueue<>();
    private final Set<TreePath> paths = new SmartHashSet<>();
    @NotNull
    private volatile Object object;
    private volatile LeafState leafState;
    private volatile @Nullable List<Node> children;
    private volatile Node loading;

    private Node(@NotNull Object object, @NotNull LeafState leafState) {
      this.object = object;
      this.leafState = leafState;
    }

    boolean isLoaded() {
      return !(object instanceof CachedTreePresentationNode);
    }

    private void setLeafState(@NotNull LeafState leafState) {
      this.leafState = leafState;
      this.children = leafState == LeafState.ALWAYS ? null : emptyList();
      this.loading = null;
    }

    private void setChildren(@NotNull List<Node> children) {
      this.leafState = LeafState.NEVER;
      this.children = children;
      this.loading = null;
    }

    private void setLoading(Node loading) {
      this.leafState = LeafState.NEVER;
      this.children = ContainerUtil.createMaybeSingletonList(loading);
      this.loading = loading;
    }

    private boolean isLoadingRequired() {
      return leafState != LeafState.ALWAYS && children == null;
    }

    private @NotNull List<Node> getChildren() {
      List<Node> list = children;
      return list != null ? list : emptyList();
    }

    private void forEachChildExceptLoading(Consumer<? super Node> consumer) {
      for (Node node : getChildren()) {
        if (node != loading) {
          consumer.accept(node);
        }
      }
    }

    private void insertPath(@NotNull TreePath path) {
      if (!paths.add(path)) {
        LOG.warn("node is already attached to " + path);
      }
      forEachChildExceptLoading(child -> child.insertPath(path.pathByAddingChild(child.object)));
    }

    private void insertMapping(Node parent) {
      if (parent == null) {
        insertPath(new CachingTreePath(object));
      }
      else if (parent.loading == this) {
        LOG.warn("insert loading node unexpectedly");
      }
      else if (parent.paths.isEmpty()) {
        LOG.warn("insert to invalid parent");
      }
      else {
        parent.paths.forEach(path -> insertPath(path.pathByAddingChild(object)));
      }
    }

    private void removePath(@NotNull TreePath path) {
      if (!paths.remove(path)) {
        LOG.warn("node is not attached to " + path);
      }
      forEachChildExceptLoading(child -> child.removePath(path.pathByAddingChild(child.object)));
    }

    private void removeMapping(Node parent, @NotNull Tree tree) {
      if (parent == null) {
        removePath(new CachingTreePath(object));
        tree.removeEmpty(this);
      }
      else if (parent.loading == this) {
        parent.loading = null;
      }
      else if (parent.paths.isEmpty()) {
        LOG.warn("remove from invalid parent");
      }
      else {
        parent.paths.forEach(path -> removePath(path.pathByAddingChild(object)));
        tree.removeEmpty(this);
      }
    }

    private void updatePaths(@NotNull Object oldObject, @NotNull Object newObject) {
      if (ContainerUtil.exists(paths, path -> contains(path, oldObject))) {
        // replace instance of user's object in all internal maps to avoid memory leaks
        List<TreePath> updated = ContainerUtil.map(paths, path -> update(path, oldObject, newObject));
        paths.clear();
        paths.addAll(updated);
        forEachChildExceptLoading(child -> child.updatePaths(oldObject, newObject));
      }
    }

    private static @NotNull TreePath update(@NotNull TreePath path, @NotNull Object oldObject, @NotNull Object newObject) {
      if (!contains(path, oldObject)) {
        return path;
      }
      if (LOG.isTraceEnabled()) LOG.debug("update path: ", path);
      Object[] objects = TreePathUtil.convertTreePathToArray(path);
      for (int i = 0; i < objects.length; i++) {
        if (oldObject == objects[i]) {
          objects[i] = newObject;
        }
      }
      return TreePathUtil.convertArrayToTreePath(objects);
    }

    private static boolean contains(@NotNull TreePath path, @NotNull Object object) {
      while (object != path.getLastPathComponent()) {
        path = path.getParentPath();
        if (path == null) {
          return false;
        }
      }
      return true;
    }
  }

  @ApiStatus.Internal
  @Override
  public void setCachedPresentation(@Nullable CachedTreePresentation presentation) {
    tree.cachedPresentation = presentation;
    if (tree.root == null && presentation != null) {
      var rootPath = new CachingTreePath(presentation.getRoot());
      tree.root = toNode(null, presentation, rootPath);
      treeStructureChanged(new CachingTreePath(tree.root.object), null, null);
    }
  }

  @Override
  protected void treeStructureChanged(TreePath path, int[] indices, Object[] children) {
    try {
      super.treeStructureChanged(path, indices, children);
    }
    catch (Throwable throwable) {
      LOG.error("custom model: " + model, throwable);
    }
  }

  public void treeStructureChanged(TreePath path) {
    treeStructureChanged(path, null, null);
  }

  @Override
  protected void treeNodesChanged(TreePath path, int[] indices, Object[] children) {
    try {
      super.treeNodesChanged(path, indices, children);
    }
    catch (Throwable throwable) {
      LOG.error("custom model: " + model, throwable);
    }
  }

  public void treeNodesChanged(TreePath path) {
    treeNodesChanged(path, null, null);
  }

  @Override
  protected void treeNodesInserted(TreePath path, int[] indices, Object[] children) {
    try {
      super.treeNodesInserted(path, indices, children);
    }
    catch (Throwable throwable) {
      LOG.error("custom model: " + model, throwable);
    }
  }

  public void treeNodesInserted(TreePath path) {
    treeNodesInserted(path, null, null);
  }

  @Override
  protected void treeNodesRemoved(TreePath path, int[] indices, Object[] children) {
    try {
      super.treeNodesRemoved(path, indices, children);
    }
    catch (Throwable throwable) {
      LOG.error("custom model: " + model, throwable);
    }
  }

  public void treeNodesRemoved(TreePath path) {
    treeNodesRemoved(path, null, null);
  }

  /**
   * @deprecated do not use
   */
  @Deprecated(forRemoval = true)
  public void setRootImmediately(@NotNull Object object) {
    Node node = new Node(object, LeafState.NEVER);
    node.insertPath(new CachingTreePath(object));
    tree.root = node;
    tree.map.put(object, node);
  }

  @ApiStatus.Internal
  public interface AsyncChildrenProvider<T> {
    @NotNull Promise<? extends List<? extends T>> getChildrenAsync(Object parent);
  }
}
