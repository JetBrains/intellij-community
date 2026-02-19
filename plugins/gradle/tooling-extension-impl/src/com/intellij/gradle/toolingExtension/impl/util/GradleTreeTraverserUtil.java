// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleTreeTraverserUtil {

  /**
   * Traverses a tree from root to leaves (Depth first).
   */
  public static <T> void depthFirstTraverseTree(
    @NotNull T root,
    @NotNull Function<T, Collection<? extends T>> action
  ) {
    Deque<T> stack = new ArrayDeque<>();
    stack.addFirst(root);
    while (!stack.isEmpty()) {
      T parent = stack.removeFirst();
      List<? extends T> children = new ArrayList<>(action.apply(parent));
      for (int i = children.size() - 1; i >= 0; i--) {
        stack.addFirst(children.get(i));
      }
    }
  }

  /**
   * Traverses a tree from root to leaves (Breadth first).
   */
  public static <T> void breadthFirstTraverseTree(
    @NotNull T root,
    @NotNull Function<T, Collection<? extends T>> action
  ) {
    Deque<T> queue = new ArrayDeque<>();
    queue.addLast(root);
    while (!queue.isEmpty()) {
      T parent = queue.removeFirst();
      Collection<? extends T> children = action.apply(parent);
      for (T child : children) {
        queue.addLast(child);
      }
    }
  }

  /**
   * Traverses a tree from leaves to root.
   */
  public static <T> void backwardTraverseTree(
    @NotNull T root,
    @NotNull Function<T, Collection<? extends T>> getChildren,
    @NotNull Consumer<T> action
  ) {
    T previous = root;

    Deque<T> stack = new ArrayDeque<>();
    stack.addFirst(root);
    while (!stack.isEmpty()) {
      T current = stack.peekFirst();
      List<? extends T> children = new ArrayList<>(getChildren.apply(current));
      if (children.isEmpty() || children.get(children.size() - 1) == previous) {
        current = stack.removeFirst();
        action.accept(current);
        previous = current;
      }
      else {
        for (int i = children.size() - 1; i >= 0; i--) {
          stack.addFirst(children.get(i));
        }
      }
    }
  }

  /**
   * Traverses a tree from root to leaves (Depth first) with a path from root to current node.
   *
   * @param action consumes visiting node and collection of nodes from root to current.
   */
  public static <T> void depthFirstTraverseTreeWithPath(
    @NotNull T root,
    @NotNull BiFunction<? super List<? extends T>, T, Collection<? extends T>> action
  ) {
    List<T> treePath = new ArrayList<>();
    depthFirstTraverseTree(new Pair<>(0, root), node -> {
      T oldParent = treePath.isEmpty() ? null : treePath.get(treePath.size() - 1);
      while (oldParent != null && treePath.size() > node.first) {
        treePath.remove(treePath.size() - 1);
        oldParent = treePath.isEmpty() ? null : treePath.get(treePath.size() - 1);
      }

      Collection<? extends T> children = action.apply(treePath, node.second);

      treePath.add(node.second);

      List<Pair<Integer, T>> childrenWithDepth = new ArrayList<>();
      for (T child : children) {
        childrenWithDepth.add(new Pair<>(node.first + 1, child));
      }
      return childrenWithDepth;
    });
  }
}
