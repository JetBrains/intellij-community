// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.AppIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class WindowManagerEx extends WindowManager {
  public enum WindowShadowMode { NORMAL, SMALL, DISABLED }

  public static WindowManagerEx getInstanceEx(){
    return (WindowManagerEx)WindowManager.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public abstract @Nullable IdeFrameImpl getFrame(@Nullable Project project);

  @Override
  public void requestUserAttention(@NotNull IdeFrame frame, boolean critical) {
    Project project = frame.getProject();
    if (project != null)
      AppIcon.getInstance().requestAttention(project, critical);
  }

  /**
   * @return focus owner of the specified window.
   * @exception IllegalArgumentException if {@code window} is {@code null}.
   */
  public abstract Component getFocusedComponent(@NotNull Window window);

  /**
   * @param project may be {@code null} when no project is opened.
   * @return focused component for the project. If project isn't specified then
   * the method returns focused component in window which has no project.
   * If there is no focused component at all then the method returns {@code null}.
   */
  public abstract @Nullable Component getFocusedComponent(@Nullable Project project);

  public abstract @Nullable Window getMostRecentFocusedWindow();

  public abstract @Nullable IdeFrame findFrameFor(@Nullable Project project);

  /**
   * @return default layout for tool windows.
   */
  @ApiStatus.Internal
  public abstract @NotNull DesktopLayout getLayout();

  /**
   * Copies {@code layout} into internal default layout.
   */
  public abstract void setLayout(@NotNull DesktopLayout layout);

  /**
   * This method is invoked by {@code IdeEventQueue} to notify window manager that
   * some window activity happens. <u><b>Do not invoke it in other places!!!<b></u>
   */
  public abstract void dispatchComponentEvent(ComponentEvent e);

  /**
   * @return union of bounds of all default screen devices. Note that {@code x} and/or {@code y}
   * coordinates can be negative. It depends on physical configuration of graphics devices.
   * For example, the left monitor has negative coordinates on Win32 platform with dual monitor support
   * (right monitor is the primer one) .
   */
  public abstract @NotNull Rectangle getScreenBounds();

  /**
   * @return bounds for the screen device for the given project frame
   */
  public abstract @Nullable Rectangle getScreenBounds(@NotNull Project project);

  public abstract void setWindowMask(Window window, Shape mask);

  public abstract void setWindowShadow(Window window, WindowShadowMode mode);

  public abstract void resetWindow(final Window window);

  public abstract void adjustContainerWindow(@NotNull Component component, Dimension oldSize, Dimension newSize);

  @ApiStatus.Internal
  public abstract @Nullable ProjectFrameHelper getFrameHelper(@Nullable Project project);

  /**
   * Find frame for project or if project is null, for a last focused window.
   */
  @ApiStatus.Internal
  public abstract @Nullable IdeFrameEx findFrameHelper(@Nullable Project project);

  /**
   * GUI test only.
   */
  @ApiStatus.Internal
  public abstract @Nullable IdeFrameEx findFirstVisibleFrameHelper();

  @ApiStatus.Internal
  public abstract void releaseFrame(@NotNull ProjectFrameHelper frameHelper);

  @ApiStatus.Internal
  public abstract @NotNull List<ProjectFrameHelper> getProjectFrameHelpers();
}
