// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.ui.tree.ChildrenProvider;
import com.intellij.ui.tree.LeafState;
import com.intellij.util.concurrency.Invoker;
import com.intellij.util.concurrency.InvokerSupplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Obsolescent;

import javax.swing.tree.TreeModel;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import static com.intellij.openapi.progress.ProgressManager.checkCanceled;
import static com.intellij.util.concurrency.EdtExecutorService.getScheduledExecutorInstance;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

final class ModelAccessor {
  private static final Logger LOG = Logger.getInstance(ModelAccessor.class);
  private final TreeModel model;
  private final Invoker invoker;

  /**
   * @param model a tree model to provide a tree structure
   */
  ModelAccessor(@NotNull TreeModel model) {
    this.model = model;
    if (model instanceof InvokerSupplier) {
      InvokerSupplier supplier = (InvokerSupplier)model;
      invoker = supplier.getInvoker();
    }
    else {
      invoker = null;
    }
  }

  /**
   * @param model a tree model to test whether it is used by the model accessor
   * @return {@code true} if the model accessor uses the same model
   */
  public boolean isActualModel(@Nullable TreeModel model) {
    return this.model == model;
  }

  /**
   * @return a promise that provides a root content on EDT and allows to cancel a request to the model
   */
  @NotNull
  public CancellablePromise<NodeContent> promiseRootContent() {
    return compute(obsolescent -> getRootContent(obsolescent));
  }

  /**
   * @param node a tree node which children are requested
   * @return a promise that provides a node structure on EDT and allows to cancel a request to the model
   */
  @NotNull
  public CancellablePromise<NodeStructure> promiseNodeStructure(@NotNull Object node) {
    return compute(obsolescent -> getNodeStructure(obsolescent, node));
  }

  @NotNull
  private <T> CancellablePromise<T> compute(@NotNull Function<? super Obsolescent, ? extends T> function) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    if (invoker != null) {
      invoker.compute(() -> function.apply((Obsolescent)promise::isDone))
        .onError(promise::setError)
        .onSuccess(result -> EventQueue.invokeLater(() -> {
          if (!promise.isDone()) promise.setResult(result);
        }));
    }
    else if (!EventQueue.isDispatchThread()) {
      EventQueue.invokeLater(() -> computeOnEDT(function, promise));
    }
    else {
      computeOnEDT(function, promise);
    }
    return promise;
  }

  private static <T> void computeOnEDT(@NotNull Function<? super Obsolescent, ? extends T> function, @NotNull AsyncPromise<T> promise) {
    assert EventQueue.isDispatchThread();
    T result;
    try {
      result = function.apply((Obsolescent)promise::isDone);
    }
    catch (IndexNotReadyException | ProcessCanceledException exception) {
      getScheduledExecutorInstance().schedule(() -> computeOnEDT(function, promise), 10, MILLISECONDS);
      return; // recalculate later
    }
    catch (Throwable error) {
      promise.setError(error);
      return;
    }
    promise.setResult(result);
  }

  @Nullable
  private NodeContent getRootContent(@NotNull Obsolescent obsolescent) {
    assert invoker != null ? invoker.isValidThread() : EventQueue.isDispatchThread();
    if (obsolescent.isObsolete()) return null;
    Object root = model.getRoot();
    if (root == null || obsolescent.isObsolete()) return null;
    return new NodeContent(root, LeafState.get(root, model));
  }

  @Nullable
  private NodeStructure getNodeStructure(@NotNull Obsolescent obsolescent, @NotNull Object node) {
    assert invoker != null ? invoker.isValidThread() : EventQueue.isDispatchThread();
    if (obsolescent.isObsolete()) return null;
    LeafState state = LeafState.get(node, model);
    if (obsolescent.isObsolete()) return null;
    List<NodeContent> list = emptyList();
    if (state != LeafState.ALWAYS) {
      if (model instanceof ChildrenProvider) {
        ChildrenProvider<?> provider = (ChildrenProvider<?>)model;
        List<?> children = provider.getChildren(node);
        if (children == null) throw new ProcessCanceledException();
        list = getChildren(obsolescent, children.size(), children::get);
      }
      else {
        list = getChildren(obsolescent, model.getChildCount(node), index -> model.getChild(node, index));
      }
    }
    if (state == LeafState.ASYNC) state = list.isEmpty() ? LeafState.ALWAYS : LeafState.NEVER;
    return new NodeStructure(new NodeContent(node, state), list);
  }

  @NotNull
  private List<NodeContent> getChildren(@NotNull Obsolescent obsolescent, int count, @NotNull IntFunction<?> function) {
    if (count < 0) LOG.warn("illegal child count: " + count);
    if (count <= 0) return emptyList();
    List<NodeContent> list = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      checkCanceled();
      if (obsolescent.isObsolete()) return emptyList();
      Object node = function.apply(index);
      if (node == null) {
        LOG.warn("ignore null child at " + index);
      }
      else if (list.stream().anyMatch(content -> content.hasUserNode(node))) {
        LOG.warn("ignore duplicated child at " + index + ": " + node);
      }
      else {
        if (obsolescent.isObsolete()) return emptyList();
        list.add(new NodeContent(node, LeafState.get(node, model)));
      }
    }
    return list;
  }

  @Override
  public String toString() {
    return model.toString();
  }


  static final class NodeContent {
    private final int hashCode;
    private final Object userNode;
    private final LeafState leafState;

    private NodeContent(@NotNull Object node, @NotNull LeafState state) {
      assert state != LeafState.DEFAULT : "resolved state required";
      hashCode = node.hashCode();
      userNode = node;
      leafState = state;
    }

    /**
     * @return an object that retrieved from a tree model
     */
    @NotNull
    public Object getUserNode() {
      return userNode;
    }

    /**
     * @return a leaf state of the corresponding object
     */
    @NotNull
    public LeafState getLeafState() {
      return leafState;
    }

    boolean hasUserNodeFrom(@NotNull NodeContent content) {
      return hasUserNode(content.userNode, content.hashCode);
    }

    boolean hasUserNode(@NotNull Object node) {
      return hasUserNode(node, node.hashCode());
    }

    boolean hasUserNode(@NotNull Object node, int hash) {
      return hashCode == hash && (userNode == node || userNode.equals(node));
    }

    @Override
    public boolean equals(Object object) {
      return object == this || object instanceof NodeContent && hasUserNodeFrom((NodeContent)object);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }


  static final class NodeStructure {
    private final NodeContent content;
    private final List<NodeContent> children;

    private NodeStructure(@NotNull NodeContent content, @NotNull List<NodeContent> children) {
      this.content = content;
      this.children = children;
    }

    /**
     * @return an updated node content
     */
    @NotNull
    public NodeContent getContent() {
      return content;
    }

    /**
     * @return a list of node children
     */
    @NotNull
    public List<NodeContent> getChildren() {
      return children;
    }
  }
}
