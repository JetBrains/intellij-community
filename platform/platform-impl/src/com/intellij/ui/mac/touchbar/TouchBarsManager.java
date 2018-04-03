// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

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
        pushTouchBar(TouchBarGeneral.instance(project));
      }
      @Override
      public void projectClosed(Project project) {
        trace("closed project %s, hide touchbar", project);
        popTouchBar();
        TouchBarGeneral.release(project);
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  synchronized public static void pushTouchBar(TouchBar bar) {
    ourTouchBarStack.push(bar);
    NST.setTouchBar(bar);
  }

  synchronized public static void popTouchBar() {
    if (ourTouchBarStack.isEmpty())
      return;

    ourTouchBarStack.pop();
    NST.setTouchBar(ourTouchBarStack.peek());
  }

  synchronized public static void closeTouchBar(TouchBar tb) {
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
