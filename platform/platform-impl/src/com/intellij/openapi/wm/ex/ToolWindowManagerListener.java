// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ToolWindowManagerListener extends EventListener {
  Topic<ToolWindowManagerListener> TOPIC = new Topic<>("tool window events", ToolWindowManagerListener.class);

  /**
   * Invoked when tool window with specified {@code id} is registered in {@link ToolWindowManager}.
   *
   * @param id {@code id} of registered tool window.
   */
  default void toolWindowRegistered(@NotNull String id) {
  }

  /**
   * Invoked when tool window with specified {@code id} is unregistered in {@link ToolWindowManager}.
   *
   * @param id {@code id} of tool window.
   * @param toolWindow
   */
  default void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
  }

  /**
   * Not fired on tool window registered and unregistered.
   */
  default void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
    stateChanged();
  }

  /**
   * Invoked when tool window with specified {@code id} is shown.
   *
   * @param id {@code id} of shown tool window.
   * @param toolWindow shown tool window
   */
  default void toolWindowShown(@NotNull String id, @NotNull ToolWindow toolWindow) {
  }

  /**
   * @deprecated Use {{@link #stateChanged(ToolWindowManager)}}
   */
  @Deprecated
  default void stateChanged() {
  }
}