// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ToolWindowManagerEx extends ToolWindowManager {
  /**
   * @deprecated Use {{@link #registerToolWindow(RegisterToolWindowTask)}}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public abstract void initToolWindow(@NotNull ToolWindowEP bean);

  @ApiStatus.Internal
  public abstract ToolWindowsPane init(ProjectFrameHelper frameHelper);

  public static @NotNull ToolWindowManagerEx getInstanceEx(@NotNull Project project) {
    return (ToolWindowManagerEx)getInstance(project);
  }

  /**
   * @deprecated Use {@link ToolWindowManagerListener#TOPIC}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener listener) {
  }

  /**
   * @deprecated Use {@link ToolWindowManagerListener#TOPIC}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener listener, @NotNull Disposable parentDisposable) {
  }

  /**
   * @deprecated Use {@link ToolWindowManagerListener#TOPIC}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void removeToolWindowManagerListener(@NotNull ToolWindowManagerListener listener) {
  }

  /**
   * @return layout of tool windows.
   */
  public abstract @NotNull DesktopLayout getLayout();

  public abstract void setLayoutToRestoreLater(@Nullable DesktopLayout layout);

  public abstract @Nullable DesktopLayout getLayoutToRestoreLater();

  /**
   * Copied {@code layout} into internal layout and rearranges tool windows.
   */
  public abstract void setLayout(@NotNull DesktopLayout layout);

  public abstract void clearSideStack();

  public boolean shouldUpdateToolWindowContent(@NotNull ToolWindow toolWindow) {
    return toolWindow.isVisible();
  }

  public abstract void hideToolWindow(@NotNull String id, boolean hideSide);

  public abstract @NotNull List<String> getIdsOn(@NotNull ToolWindowAnchor anchor);

  public static void hideToolWindowBalloon(@NotNull String id, @NotNull Project project) {
    AppUIUtil.invokeLaterIfProjectAlive(project, () -> {
      Balloon balloon = ToolWindowManager.getInstance(project).getToolWindowBalloon(id);
      if (balloon != null) {
        balloon.hide();
      }
    });
  }
}
