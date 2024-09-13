// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.CachedTreePresentationNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractTreeNodeVisitor<T> implements TreeVisitor {
  protected static final Logger LOG = Logger.getInstance(AbstractTreeNodeVisitor.class);
  private final Supplier<? extends T> supplier;
  private final Predicate<? super TreePath> predicate;

  /**
   * @param supplier  that provides an element to search in a tree
   * @param predicate that controls visiting children of found node:
   *                  {@code null} to interrupt visiting,
   *                  {@code true} to continue visiting with children,
   *                  {@code false} to continue visiting without children
   */
  public AbstractTreeNodeVisitor(@NotNull Supplier<? extends T> supplier, @Nullable Predicate<? super TreePath> predicate) {
    this.supplier = supplier;
    this.predicate = predicate;
  }

  @Override
  public @NotNull VisitThread visitThread() {
    return VisitThread.BGT;
  }

  /**
   * @return an element to search in a tree or {@code null} if it is obsolete
   */
  public final @Nullable T getElement() {
    return supplier.get();
  }

  @Override
  @RequiresBackgroundThread
  public @NotNull Action visit(@NotNull TreePath path) {
    if (LOG.isTraceEnabled()) LOG.debug("process ", path);
    T element = getElement();
    if (element == null) return Action.SKIP_SIBLINGS;
    Object object = TreeUtil.getLastUserObject(path);
    if (object instanceof AbstractTreeNode) {
      return visit(path, (AbstractTreeNode)object, element);
    }
    else if (object instanceof String) {
      LOG.debug("ignore children: ", object);
    }
    else if (object instanceof CachedTreePresentationNode) {
      // Cached presentation nodes don't contain the actual object because it's not loaded yet.
      return Action.SKIP_CHILDREN;
    }
    else {
      LOG.warn(object == null ? "no object" : "unexpected object " + object.getClass());
    }
    return Action.SKIP_CHILDREN;
  }

  /**
   * @param path    a currently visited path
   * @param node    a node of a tree structure
   * @param element an element to find
   * @return an action that controls visiting a tree
   */
  protected @NotNull Action visit(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull T element) {
    if (matches(node, element)) {
      LOG.debug("found ", path);
      if (predicate == null) return Action.INTERRUPT;
      if (predicate.test(path)) return Action.CONTINUE;
    }
    else if (contains(node, element)) {
      LOG.debug("visit ", path);
      return Action.CONTINUE;
    }
    return Action.SKIP_CHILDREN;
  }

  /**
   * @param node    a node of a tree structure
   * @param element an element to find
   * @return {@code true} if the specified node represents the given element
   */
  protected boolean matches(@NotNull AbstractTreeNode<?> node, @NotNull T element) {
    return node.canRepresent(element);
  }

  /**
   * @param node    a node of a tree structure
   * @param element an element to find
   * @return {@code true} if the specified node is an ancestor of the given element
   */
  protected boolean contains(@NotNull AbstractTreeNode<?> node, @NotNull T element) {
    T content = getContent(node);
    return content != null && isAncestor(content, element);
  }

  /**
   * @param node a node of a tree structure
   * @return a content of the specified tree or {@code null}
   */
  protected T getContent(@NotNull AbstractTreeNode node) {
    return null;
  }

  /**
   * @param content a content of a tree node
   * @param element an element to find
   * @return {@code true} if the specified content is an ancestor of the given element
   */
  protected boolean isAncestor(@NotNull T content, @NotNull T element) {
    return false;
  }
}
