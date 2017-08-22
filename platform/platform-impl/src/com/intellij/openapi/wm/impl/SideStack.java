/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.ToolWindowAnchor;

import java.util.Iterator;
import java.util.Stack;

/**
 * This is stack of tool window that were replaced by another tool windows.
 *
 * @author Vladimir Kondratyev
 */
final class SideStack {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.SideStack");
  private final Stack myStack;

  SideStack() {
    myStack = new Stack();
  }

  /**
   * Pushes {@code info} into the stack. The method stores cloned copy of original {@code info}.
   */
  void push(final WindowInfoImpl info) {
    LOG.assertTrue(info.isDocked());
    LOG.assertTrue(!info.isAutoHide());
    myStack.push(info.copy());
  }

  WindowInfoImpl pop(final ToolWindowAnchor anchor) {
    for (int i = myStack.size() - 1; true; i--) {
      final WindowInfoImpl info = (WindowInfoImpl)myStack.get(i);
      if (anchor == info.getAnchor()) {
        myStack.remove(i);
        return info;
      }
    }
  }

  /**
   * @return {@code true} if and only if there is window in the state with the same
   *         {@code anchor} as the specified {@code info}.
   */
  boolean isEmpty(final ToolWindowAnchor anchor) {
    for (int i = myStack.size() - 1; i > -1; i--) {
      final WindowInfoImpl info = (WindowInfoImpl)myStack.get(i);
      if (anchor == info.getAnchor()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Removes all {@code WindowInfo}s with the specified {@code id}.
   */
  void remove(final String id) {
    for (Iterator i = myStack.iterator(); i.hasNext();) {
      final WindowInfoImpl info = (WindowInfoImpl)i.next();
      if (id.equals(info.getId())) {
        i.remove();
      }
    }
  }

  void clear() {
    myStack.clear();
  }
}
