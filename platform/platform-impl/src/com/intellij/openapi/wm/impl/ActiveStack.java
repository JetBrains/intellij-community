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

import java.util.Iterator;
import java.util.Stack;

/**
 * Actually this class represent two stacks.
 * 1. Stack of <code>id</code>s of active tool windows. This stack is used for reactivation of tool window
 * after the another tool window was closed. This stack is cleared every time you active the editor.
 * 2. Permanent stack. It is the same as the first one, but it's not cleared when editor is being
 * activated. It used to provide id of last active tool window.
 *
 * @author Vladimir Kondratyev
 */
final class ActiveStack {
  /**
   * Contains <code>id</code>s of tool window that were activated. This stack
   * is cleared each time when editor is being activated.
   */
  private final Stack<String> myStack;
  /**
   * This stack is not cleared when editor is being activated. It means its "long"
   * persistence.
   */
  private final Stack<String> myPersistentStack;

  /**
   * Creates enabled window stack.
   */
  ActiveStack() {
    myStack = new Stack<String>();
    myPersistentStack = new Stack<String>();
  }

  /**
   * Clears stack but doesn't affect long persistence stack.
   */
  void clear() {
    myStack.clear();
  }

  /**
   * Return whether the stack of active (not persistent) <code>id</code>s is empty or not.
   */
  boolean isEmpty() {
    return myStack.isEmpty();
  }

  String pop() {
    return myStack.pop();
  }

  String peek() {
    return myStack.peek();
  }

  int getSize() {
    return myStack.size();
  }

  String peek(int i) {
    return myStack.get(getSize() - i - 1);
  }

  String[] getStack() {
    String[] result = new String[getSize()];
    for (int i = 0; i < getSize(); i++) {
      result[i] = peek(i);
    }
    return result;
  }

  String[] getPersistentStack() {
    String[] result = new String[getPersistentSize()];
    for (int i = 0; i < getPersistentSize(); i++) {
      result[i] = peekPersistent(i);  
    }
    return result;
  }

  void push(final String id) {
    remove(id, true);
    myStack.push(id);
    myPersistentStack.push(id);
  }

  int getPersistentSize() {
    return myPersistentStack.size();
  }

  /**
   * Peeks element at the persistent stack. <code>0</code> means the top of the stack.
   */
  String peekPersistent(final int index) {
    return myPersistentStack.get(myPersistentStack.size() - index - 1);
  }

  /**
   * Removes specified <code>ID</code> from stack.
   *
   * @param id                   <code>ID</code> to be removed.
   * @param removePersistentAlso if <code>true</code> then clears last active <code>ID</code>
   *                             if it's the last active <code>ID</code>.
   */
  void remove(final String id, final boolean removePersistentAlso) {
    for (Iterator i = myStack.iterator(); i.hasNext();) {
      if (id.equals(i.next())) {
        i.remove();
      }
    }
    if (removePersistentAlso) {
      for (Iterator i = myPersistentStack.iterator(); i.hasNext();) {
        if (id.equals(i.next())) {
          i.remove();
        }
      }
    }
  }
}