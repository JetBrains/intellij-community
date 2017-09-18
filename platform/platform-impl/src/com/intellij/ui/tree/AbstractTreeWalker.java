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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

abstract class AbstractTreeWalker<N> {
  private enum State {STARTED, REQUESTED, PAUSED, FINISHED, FAILED}

  private final AtomicReference<State> state = new AtomicReference<>();
  private final AsyncPromise<TreePath> promise = new AsyncPromise<>();
  private final ArrayDeque<ArrayDeque<N>> stack = new ArrayDeque<>();
  private final Function<N, Object> converter;
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
  public AbstractTreeWalker(@NotNull TreeVisitor visitor, Function<N, Object> converter) {
    this.converter = converter;
    this.visitor = visitor;
  }

  /**
   * Returns a list of child nodes for the specified node.
   * This method is called by the walker only if the visitor
   * returned the {@link TreeVisitor.Action#CONTINUE CONTINUE} action.
   * The walker will be paused if it returns {@code null}.
   * To continue user should call the {@link #setChildren} method.
   *
   * @param node a node in a tree structure
   * @return children for the specified node or {@code null} if children will be set later
   */
  protected abstract Collection<N> getChildren(@NotNull N node);

  /**
   * Sets the children, awaited by the walker, and continues to traverse a tree structure.
   *
   * @param children a list of child nodes for the node specified in the {@link #getChildren} method
   * @throws IllegalStateException if it is called in unexpected state
   */
  public void setChildren(Collection<N> children) {
    boolean paused = state.compareAndSet(State.PAUSED, State.STARTED);
    if (!paused && !state.compareAndSet(State.REQUESTED, State.STARTED)) throw new IllegalStateException();
    stack.push(children == null ? new ArrayDeque<>() : new ArrayDeque<>(children));
    if (paused) processNextPath();
  }

  /**
   * @return a promise that will be resolved when visiting is finished
   */
  @NotNull
  public Promise<TreePath> promise() {
    return promise;
  }

  /**
   * Stops visiting a tree structure by specifying a cause.
   */
  public void setError(@NotNull Throwable error) {
    state.set(State.FAILED);
    promise.setError(error);
  }

  /**
   * Starts visiting a tree structure from the specified root node.
   *
   * @param node a tree root or {@code null} if nothing to traverse
   */
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
        switch (visitor.accept(path)) {
          case CONTINUE:
            update(null, State.REQUESTED);
            if (processChildren(path, node)) processNextPath();
            return;
          case INTERRUPT:
            result = path;
            break;
          case SKIP_CHILDREN:
            break;
          case SKIP_SIBLINGS:
            break;
        }
      }
      catch (Exception error) {
        setError(error);
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
          switch (visitor.accept(path)) {
            case CONTINUE:
              update(State.STARTED, State.REQUESTED);
              if (processChildren(path, node)) break;
              return; // stop processing and wait for setChildren
            case INTERRUPT:
              update(State.STARTED, State.FINISHED);
              current = null;
              stack.clear();
              promise.setResult(path);
              return; // path is found
            case SKIP_SIBLINGS:
              siblings.clear();
              break;
            case SKIP_CHILDREN:
              break;
          }
        }
      }
    }
    catch (Exception error) {
      setError(error);
    }
  }

  private void update(State expected, State replacement) {
    if (!state.compareAndSet(expected, replacement)) throw new IllegalStateException();
  }
}
