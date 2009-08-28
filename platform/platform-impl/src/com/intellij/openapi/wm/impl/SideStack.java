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
   * Pushes <code>info</code> into the stack. The method stores cloned copy of original <code>info</code>.
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
   * @return <code>true</code> if and only if there is window in the state with the same
   *         <code>anchor</code> as the specified <code>info</code>.
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
   * Removes all <code>WindowInfo</code>s with the specified <code>id</code>.
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
