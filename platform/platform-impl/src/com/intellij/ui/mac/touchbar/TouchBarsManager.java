// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;

public class TouchBarsManager {
  private final static boolean IS_LOGGING_ENABLED = false;
  private static final Logger LOG = Logger.getInstance(TouchBar.class);
  private static final ArrayDeque<TouchBar> ourTouchBarStack = new ArrayDeque<>();

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        trace("opened project %s, set general touchbar", project);
        showTouchBar(TouchBarGeneral.instance(project));

        final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
        twm.addToolWindowManagerListener(new ToolWindowManagerListener() {
          @Override
          public void toolWindowRegistered(@NotNull String id) {}
          @Override
          public void stateChanged() {
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && activeId.equals("Debug"))
              showTouchBar(TouchBarDebugger.instance(project));
            else {
              TouchBarDebugger tbd = TouchBarDebugger.findInstance(project);
              if (tbd != null)
                closeTouchBar(tbd);
            }
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        trace("closed project %s, hide touchbar", project);
        closeTouchBar(TouchBarGeneral.instance(project));
        TouchBarGeneral.release(project);

        // TODO: implement _closeAllProjectBars (to destroy all project-dependent bars : Debug, Editor, e.t.c.)
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  synchronized public static void showTouchBar(@NotNull TouchBar bar) {
    final TouchBar top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    ourTouchBarStack.remove(bar);
    ourTouchBarStack.push(bar);
    NST.setTouchBar(bar);
  }

  synchronized public static void closeTouchBar(@NotNull TouchBar tb) {
    if (ourTouchBarStack.isEmpty())
      return;

    final TouchBar top = ourTouchBarStack.peek();
    if (top == tb) {
      ourTouchBarStack.pop();
      NST.setTouchBar(ourTouchBarStack.peek());
    } else {
      ourTouchBarStack.remove(tb);
    }
  }

  private static void trace(String fmt, Object... args) {
    if (IS_LOGGING_ENABLED)
      LOG.trace(String.format(fmt, args));
  }
}
