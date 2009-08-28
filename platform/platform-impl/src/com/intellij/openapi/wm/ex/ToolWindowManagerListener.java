package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.ToolWindowManager;

import java.util.EventListener;

public interface ToolWindowManagerListener extends EventListener{
  /**
   * Invoked when tool window with specified <code>id</code> is registered in <code>ToolWindowMnagare</code>.
   * @param id <code>id</code> of registered tool window.
   */
  void toolWindowRegistered(String id);

  void stateChanged();
}