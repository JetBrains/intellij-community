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
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.CommandProcessor;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class WindowManagerEx extends WindowManager {
  public static enum WindowShadowMode { NORMAL, SMALL, DISABLED }

  public static WindowManagerEx getInstanceEx(){
    return (WindowManagerEx)WindowManager.getInstance();
  }


  public abstract IdeFrameImpl getFrame(Project project);

  public abstract IdeFrameImpl allocateFrame(Project project);

  public abstract void releaseFrame(IdeFrameImpl frame);

  /**
   * @return focus owner of the specified window.
   * @exception IllegalArgumentException if <code>window</code> is <code>null</code>.
   */
  public abstract Component getFocusedComponent(@NotNull Window window);

  /**
   * @param project may be <code>null</code> when no project is opened.
   * @return focused component for the project. If project isn't specified then
   * the method returns focused component in window which has no project.
   * If there is no focused component at all then the method returns <code>null</code>.
   */
  public abstract Component getFocusedComponent(Project project);

  public abstract Window getMostRecentFocusedWindow();

  public abstract IdeFrame findFrameFor(@Nullable Project project);

  public abstract CommandProcessor getCommandProcessor();

  /**
   * @return default layout for tool windows.
   */
  public abstract DesktopLayout getLayout();

  /**
   * Copies <code>layout</code> into internal default layout.
   */
  public abstract void setLayout(DesktopLayout layout);

  /**
   * This method is invoked by <code>IdeEventQueue</code> to notify window manager that
   * some window activity happens. <u><b>Do not invoke it in other places!!!<b></u>
   */
  public abstract void dispatchComponentEvent(ComponentEvent e);

  /**
   * @return union of bounds of all default screen devices. Note that <code>x</code> and/or <code>y</code>
   * coordinates can be negative. It depends on phisical configuration of graphics devices.
   * For example, the left monitor has negative coordinates on Win32 platform with dual monitor support
   * (right monitor is the primer one) .
   */
  public abstract Rectangle getScreenBounds();

  /**
   * @return bounds for the screen device for the given project frame
   */
  public abstract Rectangle getScreenBounds(@NotNull final Project project);

  /**
   * @return <code>true</code> is and only if current OS supports alpha mode for windows and
   * all native libraries were successfully loaded.
   */
  public abstract boolean isAlphaModeSupported();

  /**
   * Sets alpha (transparency) ratio for the specified <code>window</code>.
   * If alpha mode isn't supported by underlying windowing system then the method does nothing.
   * The method also does nothing if alpha mode isn't enabled for the specified <code>window</code>.
   * @param window <code>window</code> which transparency should be changed.
   * @param ratio ratio of transparency. <code>0</code> means absolutely non transparent window.
   * <code>1</code> means absolutely transparent window.
   * @exception java.lang.IllegalArgumentException if <code>window</code> is not
   * desplayable or not showing.
   * @exception java.lang.IllegalArgumentException if <code>ration</code> isn't
   * in <code>[0..1]</code> range.
   */
  public abstract void setAlphaModeRatio(Window window,float ratio);

  public abstract void setWindowMask(Window window, Shape mask);

  public abstract void setWindowShadow(Window window, WindowShadowMode mode);

  public abstract void resetWindow(final Window window);

  /**
   * @return <code>true</code> if specified <code>window</code> is currently is alpha mode.
   */
  public abstract boolean isAlphaModeEnabled(Window window);

  /**
   * Sets whether the alpha (transparent) mode is enabled for specified <code>window</code>.
   * If alpha mode isn't supported by underlying windowing system then the method does nothing.
   * @param window window which mode to be set.
   * @param state determines the new alpha mode.
   */
  public abstract void setAlphaModeEnabled(Window window,boolean state);

  /**
   * Either dispose the dialog immediately if project's frame has focus or just hide and dispose when frame gets focus or closes.
   * @param dialog to hide and dispose later
   * @param project the dialog has been shown for
   */
  public abstract void hideDialog(JDialog dialog, Project project);

  public abstract void adjustContainerWindow(Component c, Dimension oldSize, Dimension newSize);


}
