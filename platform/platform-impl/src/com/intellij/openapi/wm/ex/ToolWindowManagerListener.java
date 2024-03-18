// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  Topic<ToolWindowManagerListener> TOPIC = new Topic<>(ToolWindowManagerListener.class, Topic.BroadcastDirection.TO_PARENT);

  /**
   * @deprecated Use {@link #toolWindowsRegistered}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  default void toolWindowRegistered(@SuppressWarnings("unused") @NotNull String id) {
  }

  /**
   * WARNING: The listener MIGHT be called NOT ON EDT.
   */
  default void toolWindowsRegistered(@NotNull List<String> ids, @NotNull ToolWindowManager toolWindowManager) {
    for (String id : ids) {
      toolWindowRegistered(id);
    }
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

  default void stateChanged(@NotNull ToolWindowManager toolWindowManager,
                            @NotNull ToolWindowManagerListener.ToolWindowManagerEventType changeType) {
    stateChanged(toolWindowManager);
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  default void stateChanged(@NotNull ToolWindowManager toolWindowManager,
                            @NotNull ToolWindow toolWindow,
                            @NotNull ToolWindowManagerListener.ToolWindowManagerEventType changeType) {
    stateChanged(toolWindowManager, changeType);
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
  @Deprecated(forRemoval = true)
  default void toolWindowShown(@NotNull String id, @NotNull ToolWindow toolWindow) {
  }

  /**
   * @deprecated Use {{@link #stateChanged(ToolWindowManager)}}
   */
  @Deprecated
  default void stateChanged() {
  }

  enum ToolWindowManagerEventType {
    ActivateToolWindow, HideToolWindow, RegisterToolWindow, SetContentUiType, SetLayout, SetShowStripeButton,
    SetSideTool, SetSideToolAndAnchor, SetToolWindowAnchor, SetToolWindowAutoHide, SetToolWindowType, SetVisibleOnLargeStripe,
    ShowToolWindow, UnregisterToolWindow, ToolWindowAvailable, ToolWindowUnavailable, MovedOrResized,
    MoreButtonUpdated, ShowNames, SideCustomWidth
  }
}