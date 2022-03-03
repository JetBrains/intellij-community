// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public interface ToolWindowManagerListener extends EventListener {
  @Topic.ProjectLevel
  Topic<ToolWindowManagerListener> TOPIC = new Topic<>("tool window events", ToolWindowManagerListener.class, Topic.BroadcastDirection.NONE);

  /**
   * @deprecated Use {@link #toolWindowsRegistered}
   */
  @Deprecated
  default void toolWindowRegistered(@SuppressWarnings("unused") @NotNull String id) {
  }

  default void toolWindowsRegistered(@NotNull List<String> ids, @NotNull ToolWindowManager toolWindowManager) {
    ids.forEach(this::toolWindowRegistered);
  }

  /**
   * Invoked when tool window with specified {@code id} is unregistered in {@link ToolWindowManager}.
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
   * Invoked when tool window is shown.
   *
   * @param toolWindow shown tool window
   */
  default void toolWindowShown(@NotNull ToolWindow toolWindow) {
    toolWindowShown(toolWindow.getId(), toolWindow);
  }

  /**
   * @deprecated use {@link #toolWindowShown(ToolWindow)} instead
   */
  @SuppressWarnings("unused")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  default void toolWindowShown(@NotNull String id, @NotNull ToolWindow toolWindow) {
  }

  /**
   * @deprecated Use {{@link #stateChanged(ToolWindowManager)}}
   */
  @Deprecated
  default void stateChanged() {
  }
}