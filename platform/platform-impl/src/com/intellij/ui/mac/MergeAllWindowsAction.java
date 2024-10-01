// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeDependentAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.platform.jbr.JdkEx;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
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
    @Nullable Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    Window window = Objects.requireNonNull(ComponentUtil.getWindow(component));

    mergeAllWindows(window, true);
  }

  private static void mergeAllWindows(@NotNull Window window, boolean updateTabBars) {
    for (IdeFrame helper : WindowManager.getInstance().getAllProjectFrames()) {
      IdeFrameImpl frame = ((ProjectFrameHelper)helper).getFrame();
      if (frame != window && helper.isInFullScreen()) {
        frame.getRootPane().putClientProperty(MacMainFrameDecorator.IGNORE_EXIT_FULL_SCREEN, true);
      }
    }

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

  private static final class RecentProjectsFullScreenTabSupport implements AppLifecycleListener {
    @Override
    public void appStarted() {
      Logger logger = Logger.getInstance(MergeAllWindowsAction.class);
      if (JdkEx.isTabbingModeAvailable()) {
        IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();

        if (frames.length > 1) {
          for (IdeFrame frame : frames) {
            if (!frame.isInFullScreen()) {
              IdeFrameImpl ideFrame = ((ProjectFrameHelper)frame).getFrame();
              logger.info("=== FullScreenTabSupport: no fullscreen frame: " + ideFrame + " ===");
              JRootPane pane = ideFrame.getRootPane();
              if (pane == null) {
                logger.info("=== FullScreenTabSupport: no root pane for frame: " + ideFrame + " ===");
                return;
              }
              if (pane.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) == null &&
                  pane.getClientProperty(MacMainFrameDecorator.FULL_SCREEN_PROGRESS) == null) {
                return;
              }
              logger.info("=== FullScreenTabSupport: fullscreen in progress for frame: " + ideFrame + " ===");
            }
          }

          int state = Foundation.invoke("NSWindow", "userTabbingPreference").intValue();
          if (state == 2/*NSWindowUserTabbingPreferenceInFullScreen*/) {
            logger.info("=== FullScreenTabSupport: run auto mergeAllWindows on start ===");
            mergeAllWindows(Objects.requireNonNull(((ProjectFrameHelper)frames[0]).getFrame()), false);
          }
          else {
            logger.info("=== FullScreenTabSupport: settings: " + state + " ===");
          }
        }
        else {
          logger.info("=== FullScreenTabSupport: frames: " + frames.length + " ===");
        }
      }
      else if (SystemInfoRt.isMac) {
        logger.info("=== FullScreenTabSupport: off ===");
      }
    }
  }
}