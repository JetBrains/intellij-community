// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import javax.swing.tree.TreePath

@ApiStatus.Internal
abstract class TreeWalkerBase<N : Any> {

  /**
   * @return a promise that will be resolved when visiting is finished
   */
  abstract fun promise(): Promise<TreePath>

  /**
   * Starts visiting a tree structure from the specified root node.
   *
   * @param node a tree root or {@code null} if nothing to traverse
   */
  abstract fun start(node: N?)

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
  protected abstract fun getChildren(node: N): Collection<N>?

  /**
   * Sets the children, awaited by the walker, and continues to traverse a tree structure.
   *
   * @param children a list of child nodes for the node specified in the {@link #getChildren} method
   * @throws IllegalStateException if it is called in unexpected state
   */
  abstract fun setChildren(children: Collection<N>)

  /**
   * Stops visiting a tree structure by specifying a cause.
   */
  abstract fun setError(error: Throwable)

}
