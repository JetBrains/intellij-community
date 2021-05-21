// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

/**
 * This is stack of tool window that were replaced by another tool windows.
 */
final class SideStack {
  private static final Logger LOG = Logger.getInstance(SideStack.class);
  private final Stack<WindowInfoImpl> stack = new Stack<>();

  /**
   * Pushes {@code info} into the stack. The method stores cloned copy of original {@code info}.
   */
  void push(@NotNull WindowInfoImpl info) {
    LOG.assertTrue(info.isDocked());
    LOG.assertTrue(!info.isAutoHide());
    stack.push(info.copy());
  }

  WindowInfoImpl pop(@NotNull ToolWindowAnchor anchor) {
    for (int i = stack.size() - 1; true; i--) {
      WindowInfoImpl info = stack.get(i);
      if (anchor == info.getAnchor()) {
        stack.remove(i);
        return info;
      }
    }
  }

  /**
   * @return {@code true} if and only if there is window in the state with the same
   *         {@code anchor} as the specified {@code info}.
   */
  boolean isEmpty(@NotNull ToolWindowAnchor anchor) {
    for (int i = stack.size() - 1; i > -1; i--) {
      WindowInfoImpl info = stack.get(i);
      if (anchor == info.getAnchor()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Removes all {@code WindowInfo}s with the specified {@code id}.
   */
  void remove(String id) {
    stack.removeIf(info -> id.equals(info.getId()));
  }

  void clear() {
    stack.clear();
  }
}
