// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeDependentAction;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
public final class MergeAllWindowsAction extends IdeDependentAction {

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
    super.update(e);
  }

  public static boolean isTabbedWindow(@NotNull JFrame frame) {
    if (JdkEx.isTabbingModeAvailable() && WindowManager.getInstance().getAllProjectFrames().length > 1) {
      ID id = MacUtil.getWindowFromJavaWindow(frame);
      int tabs = Foundation.invoke(Foundation.invoke(id, "tabbedWindows"), "count").intValue();
      return tabs > 1;
    }
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window window = Objects.requireNonNull(UIUtil.getWindow(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)));

    mergeAllWindows(window, true);
  }

  private static void mergeAllWindows(@NotNull Window window, boolean updateTabBars) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID id = MacUtil.getWindowFromJavaWindow(window);
      Foundation.invoke(id, "mergeAllWindows:", ID.NIL);
      if (MacWinTabsHandler.isVersion2()) {
        ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMerge());
      }
      else if (updateTabBars) {
        ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandler.updateTabBars(null));
      }
    });
  }

  private static class RecentProjectsFullScreenTabSupport implements AppLifecycleListener {
    @Override
    public void appStarted() {
      if (JdkEx.isTabbingModeAvailable()) {
        IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();

        if (frames.length > 1) {
          for (IdeFrame frame : frames) {
            if (!frame.isInFullScreen()) {
              return;
            }
          }

          if (Foundation.invoke("NSWindow", "userTabbingPreference").intValue() == 2/*NSWindowUserTabbingPreferenceInFullScreen*/) {
            mergeAllWindows(Objects.requireNonNull(((ProjectFrameHelper)frames[0]).getFrame()), false);
          }
        }
      }
    }
  }
}