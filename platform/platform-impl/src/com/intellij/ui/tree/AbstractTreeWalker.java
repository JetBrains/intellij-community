// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract class AbstractTreeWalker<N> extends TreeWalkerBase<N> {
  private enum State {STARTED, REQUESTED, PAUSED, FINISHED, FAILED}

  private final AtomicReference<State> state = new AtomicReference<>();
  private final AsyncPromise<TreePath> promise = new AsyncPromise<>();
  private final ArrayDeque<ArrayDeque<N>> stack = new ArrayDeque<>();
  private final Function<? super N, Object> converter;
  private final TreeVisitor visitor;
  private volatile TreePath current;

  /**
   * Creates a new tree walker with the specified tree visitor.
   *
   * @param visitor an object that controls visiting a tree structure
   */
  public AbstractTreeWalker(@NotNull TreeVisitor visitor) {
    this(visitor, node -> node);
  }

  /**
   * Creates a new tree walker with the specified node converter,
   * which allows to generate a tree path expected by the given tree visitor.
   *
   * @param visitor   an object that controls visiting a tree structure
   * @param converter a node converter for the path components
   */
  public AbstractTreeWalker(@NotNull TreeVisitor visitor, Function<? super N, Object> converter) {
    this.converter = converter;
    this.visitor = visitor;
  }

  @Override
  public void setChildren(@NotNull Collection<? extends N> children) {
    boolean paused = state.compareAndSet(State.PAUSED, State.STARTED);
    if (!paused && !state.compareAndSet(State.REQUESTED, State.STARTED)) throw new IllegalStateException();
    stack.push(new ArrayDeque<>(children));
    if (paused) processNextPath();
  }

  @Override
  public @NotNull Promise<TreePath> promise() {
    return promise;
  }

  @Override
  public void setError(@NotNull Throwable error) {
    state.set(State.FAILED);
    promise.setError(error);
  }

  @Override
  public void start(N node) {
    start(null, node);
  }

  /**
   * Starts visiting a tree structure from the specified node.
   *
   * @param parent a parent tree path or {@code null} for a root node
   * @param node   a tree node or {@code null} if nothing to traverse
   */
  public void start(TreePath parent, N node) {
    TreePath result = null;
    if (node != null) {
      try {
        TreePath path = TreePathUtil.createTreePath(parent, converter.apply(node));
        switch (visitor.visit(path)) {
          case CONTINUE -> {
            update(null, State.REQUESTED);
            if (processChildren(path, node)) processNextPath();
            return;
          }
          case INTERRUPT -> result = path;
          case SKIP_CHILDREN, SKIP_SIBLINGS -> {
          }
        }
      }
      catch (Exception error) {
        setError(error);
        return;
      }
    }
    update(null, State.FINISHED);
    promise.setResult(result);
  }

  /**
   * @param path a path to the specified node
   * @param node a node to get children to process
   * @return {@code false} if the walker should be pause
   */
  private boolean processChildren(@NotNull TreePath path, @NotNull N node) {
    current = path;
    Collection<N> children = getChildren(node);
    if (children == null) return !state.compareAndSet(State.REQUESTED, State.PAUSED);
    update(State.REQUESTED, State.STARTED);
    stack.push(new ArrayDeque<>(children));
    return true;
  }

  private void processNextPath() {
    try {
      while (State.STARTED == state.get()) {
        ArrayDeque<N> siblings = stack.peek();
        if (siblings == null) {
          update(State.STARTED, State.FINISHED);
          current = null;
          promise.setResult(null);
          return; // nothing to process
        }
        N node = siblings.poll();
        if (node == null) {
          TreePath path = current;
          if (path == null) throw new IllegalStateException();
          if (siblings != stack.poll()) throw new IllegalStateException();
          current = path.getParentPath();
        }
        else {
          TreePath path = TreePathUtil.createTreePath(current, converter.apply(node));
          switch (visitor.visit(path)) {
            case CONTINUE -> {
              update(State.STARTED, State.REQUESTED);
              if (processChildren(path, node)) break;
              return; // stop processing and wait for setChildren
            }
            case INTERRUPT -> {
              update(State.STARTED, State.FINISHED);
              current = null;
              stack.clear();
              promise.setResult(path);
              return; // path is found
            }
            case SKIP_SIBLINGS -> siblings.clear();
            case SKIP_CHILDREN -> {}
          }
        }
      }
    }
    catch (Exception error) {
      setError(error);
    }
  }

  private void update(State expected, @NotNull State replacement) {
    if (!state.compareAndSet(expected, replacement)) throw new IllegalStateException();
  }
}
