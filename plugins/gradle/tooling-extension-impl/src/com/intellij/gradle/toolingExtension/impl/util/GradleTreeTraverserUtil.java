// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GradleTreeTraverserUtil {

  /**
   * Traverses a tree from root to leaf.
   */
  public static <T> void traverseTree(
    @NotNull T root,
    @NotNull Function<T, Iterable<? extends T>> getChildren,
    @NotNull Consumer<T> action
  ) {
    Queue<T> queue = new ArrayDeque<>();
    action.accept(root);
    queue.add(root);
    while (!queue.isEmpty()) {
      T parent = queue.remove();
      for (T child : getChildren.apply(parent)) {
        action.accept(child);
        queue.add(child);
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
    stack.push(root);
    while (!stack.isEmpty()) {
      T current = stack.peek();
      List<? extends T> children = new ArrayList<>(getChildren.apply(current));
      if (children.isEmpty() || children.get(children.size() - 1) == previous) {
        current = stack.pop();
        action.accept(current);
        previous = current;
      }
      else {
        for (int i = children.size() - 1; i >= 0; i--) {
          stack.push(children.get(i));
        }
      }
    }
  }
}
