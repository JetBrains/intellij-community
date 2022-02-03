// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.toolWindow.ToolWindowEntry;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

/**
 * Actually this class represent two stacks.
 * 1. Stack of {@code id}s of active tool windows. This stack is used for reactivation of tool window
 * after the another tool window was closed. This stack is cleared every time you active the editor.
 * 2. Permanent stack. It is the same as the first one, but it's not cleared when editor is being
 * activated. It used to provide id of last active tool window.
 *
 * @author Vladimir Kondratyev
 */
final class ActiveStack {
  /**
   * Contains {@code id}s of tool window that were activated. This stack
   * is cleared each time when editor is being activated.
   */
  private final Stack<ToolWindowEntry> myStack = new Stack<>();
  /**
   * This stack is not cleared when editor is being activated. It means its "long"
   * persistence.
   */
  private final Stack<ToolWindowEntry> myPersistentStack = new Stack<>();

  /**
   * Creates enabled window stack.
   */
  ActiveStack() {
  }

  /**
   * Clears stack but doesn't affect long persistence stack.
   */
  void clear() {
    myStack.clear();
  }

  private int getSize() {
    return myStack.size();
  }

  @NotNull
  private ToolWindowEntry peek(int i) {
    return myStack.get(getSize() - i - 1);
  }

  ToolWindowEntry @NotNull [] getStack() {
    ToolWindowEntry[] result = new ToolWindowEntry[getSize()];
    for (int i = 0; i < getSize(); i++) {
      result[i] = peek(i);
    }
    return result;
  }

  ToolWindowEntry @NotNull [] getPersistentStack() {
    ToolWindowEntry[] result = new ToolWindowEntry[getPersistentSize()];
    for (int i = 0; i < getPersistentSize(); i++) {
      result[i] = peekPersistent(i);
    }
    return result;
  }

  void push(@NotNull ToolWindowEntry id) {
    remove(id, true);
    myStack.push(id);
    myPersistentStack.push(id);
  }

  int getPersistentSize() {
    return myPersistentStack.size();
  }

  /**
   * Peeks element at the persistent stack. {@code 0} means the top of the stack.
   */
  @NotNull
  ToolWindowEntry peekPersistent(final int index) {
    return myPersistentStack.get(myPersistentStack.size() - index - 1);
  }

  /**
   * Removes specified {@code ID} from stack.
   *
   * @param id                   {@code ID} to be removed.
   * @param removePersistentAlso if {@code true} then clears last active {@code ID}
   *                             if it's the last active {@code ID}.
   */
  void remove(@NotNull ToolWindowEntry id, boolean removePersistentAlso) {
    myStack.remove(id);
    if (removePersistentAlso) {
      myPersistentStack.remove(id);
    }
  }
}