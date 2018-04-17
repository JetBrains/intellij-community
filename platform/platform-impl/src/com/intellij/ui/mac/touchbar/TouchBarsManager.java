// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.util.ArrayDeque;

public class TouchBarsManager {
  private final static boolean IS_LOGGING_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(TouchBar.class);
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static TouchBar ourCurrentBar;

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        trace("opened project %s, set general touchbar", project);
        showTouchBar(ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.GENERAL));

        final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
        twm.addToolWindowManagerListener(new ToolWindowManagerListener() {
          private BarContainer myDebuggerBar = ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.DEBUGGER);

          @Override
          public void toolWindowRegistered(@NotNull String id) {}
          @Override
          public void stateChanged() {
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && activeId.equals("Debug"))
              showTouchBar(myDebuggerBar);
            else
              closeTouchBar(myDebuggerBar);
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        trace("closed project %s, hide touchbar", project);
        closeTouchBar(ProjectBarsStorage.instance(project).getBarContainer(ProjectBarsStorage.GENERAL));
        ProjectBarsStorage.instance(project).releaseAll();
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  public static void onKeyEvent(KeyEvent e) {
    if (!isTouchBarAvailable())
      return;

    if (
      e.getID() != KeyEvent.KEY_PRESSED
      && e.getID() != KeyEvent.KEY_RELEASED
    )
      return;

    if (
      e.getKeyCode() != KeyEvent.VK_CONTROL
      && e.getKeyCode() != KeyEvent.VK_ALT
      && e.getKeyCode() != KeyEvent.VK_META
    )
      return;

    long keymask = e.getModifiersEx(); // TODO: calc + ensure
    synchronized (TouchBarsManager.class) {
      for (BarContainer itb: ourTouchBarStack) {
        if (itb instanceof MultiBarContainer)
          ((MultiBarContainer)itb).selectBarByKeyMask(keymask);
      }

      _setTouchBar(ourTouchBarStack.peek());
    }
  }

  synchronized public static void showTouchBar(@NotNull BarContainer bar) {
    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    ourTouchBarStack.remove(bar);
    ourTouchBarStack.push(bar);
    _setTouchBar(bar.get());
  }

  synchronized public static void closeTouchBar(@NotNull BarContainer tb) {
    if (ourTouchBarStack.isEmpty())
      return;

    BarContainer top = ourTouchBarStack.peek();
    if (top == tb) {
      ourTouchBarStack.pop();
      _setTouchBar(ourTouchBarStack.peek());
    } else {
      ourTouchBarStack.remove(tb);
    }
  }


  private static void _setTouchBar(@NotNull BarContainer barProvider) { _setTouchBar(barProvider == null ? null : barProvider.get()); }

  private static void _setTouchBar(TouchBar bar) {
    if (ourCurrentBar == bar)
      return;

    ourCurrentBar = bar;
    NST.setTouchBar(bar);
  }

  private static void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      LOG.trace(String.format(fmt, args));
  }
}
