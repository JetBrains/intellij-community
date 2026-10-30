// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.AppIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.List;

public abstract class WindowManagerEx extends WindowManager {
  @ApiStatus.Internal
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

  public abstract @Nullable IdeFrame findFrameFor(@Nullable Project project);

  /**
   * This method is invoked by {@code IdeEventQueue} to notify window manager that
   * some window activity happens. <u><b>Do not invoke it in other places!!!<b></u>
   */
  @ApiStatus.Internal
  public abstract void dispatchComponentEvent(ComponentEvent e);

  /**
   * @return union of bounds of all default screen devices. Note that {@code x} and/or {@code y}
   * coordinates can be negative. It depends on the physical configuration of graphics devices.
   * For example, the left monitor has negative coordinates on the Win32 platform with dual-monitor support
   *  (the right monitor is the primer one).
   */
  public abstract @NotNull Rectangle getScreenBounds();

  /**
   * @return bounds for the screen device for the given project frame
   */
  public abstract @Nullable Rectangle getScreenBounds(@NotNull Project project);

  public abstract void setWindowMask(Window window, Shape mask);

  @ApiStatus.Internal
  public abstract void setWindowShadow(Window window, WindowShadowMode mode);

  public abstract void resetWindow(final Window window);

  @ApiStatus.Internal
  public abstract @Nullable ProjectFrameHelper getFrameHelper(@Nullable Project project);

  /**
   * Find frame for a project or if a project is null, for a last focused window.
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

  public abstract boolean isFrameReused(@NotNull ProjectFrameHelper frameHelper);

  @ApiStatus.Internal
  public abstract @NotNull List<ProjectFrameHelper> getProjectFrameHelpers();

  @ApiStatus.Internal
  public @NotNull AutoCloseable withFrameReuseEnabled() {
    return () -> {};
  }

  /**
   * The project that owns the frame that shows {@code project}: {@code project} itself unless it is a frame guest,
   * a project with no frame of its own that the frame of another project shows.
   */
  @ApiStatus.Internal
  public @NotNull Project getFrameOwner(@NotNull Project project) {
    return project;
  }

  /** Tells whether {@code project} is a frame guest, see {@link #getFrameOwner(Project)}. */
  @ApiStatus.Internal
  public boolean isFrameGuest(@NotNull Project project) {
    return false;
  }

  /** The frame guests of {@code frameOwner}, in the order they joined. */
  @ApiStatus.Internal
  public @NotNull List<Project> getFrameGuests(@NotNull Project frameOwner) {
    return Collections.emptyList();
  }

  /**
   * The active project of the frame that shows {@code project}: the project that the frame UI gives to an action.
   * The owner of the frame when the frame has no active guest.
   */
  @ApiStatus.Internal
  public @NotNull Project getActiveProject(@NotNull Project project) {
    return project;
  }

  /**
   * Makes {@code project} the active project of the frame that shows it. Any thread.
   * On the EDT the frame shows the change before the call returns.
   */
  @ApiStatus.Internal
  public void setActiveProject(@NotNull Project project) {
  }

  /**
   * Records {@code guest} as a frame guest of {@code frameOwner}. The frame of {@code frameOwner} may not exist yet.
   * A guest leaves its frame when it closes.
   */
  @ApiStatus.Internal
  public void addFrameGuest(@NotNull Project frameOwner, @NotNull Project guest) {
  }

  /** Takes {@code guest} out of its frame. For a guest that did not load, because a guest that closes leaves on its own. */
  @ApiStatus.Internal
  public void removeFrameGuest(@NotNull Project guest) {
  }
}
