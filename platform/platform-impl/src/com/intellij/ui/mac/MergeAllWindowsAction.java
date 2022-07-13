// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
public class MergeAllWindowsAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (JdkEx.isTabbingModeAvailable()) {
      presentation.setVisible(true);
      IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();
      if (frames.length > 1) {
        ID id = MacUtil.getWindowFromJavaWindow((((ProjectFrameHelper)frames[0]).getFrame()));
        int tabs = Foundation.invoke(Foundation.invoke(id, "tabbedWindows"), "count").intValue();
        presentation.setEnabled(frames.length != tabs);
      }
      else {
        presentation.setEnabled(false);
      }
    }
    else {
      presentation.setEnabledAndVisible(false);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window window = Objects.requireNonNull(UIUtil.getWindow(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)));

    mergeAllWindows(window);
  }

  private static void mergeAllWindows(Window window) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID id = MacUtil.getWindowFromJavaWindow(window);
      Foundation.invoke(id, "mergeAllWindows:", ID.NIL);
    });
  }

  private static class RecentProjectsFullScreenTabSupport implements AppLifecycleListener {
    @Override
    public void appStarted() {
      if (JdkEx.isTabbingModeAvailable()) {
        IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();
        if (frames.length == 0) {
          return;
        }

        if (frames.length > 1) {
          for (IdeFrame frame : frames) {
            if (!frame.isInFullScreen()) {
              return;
            }
          }
        }

        if (Foundation.invoke("NSWindow", "userTabbingPreference").intValue() == 2/*NSWindowUserTabbingPreferenceInFullScreen*/) {
          mergeAllWindows(((ProjectFrameHelper)frames[0]).getFrame());
        }
      }
    }
  }
}