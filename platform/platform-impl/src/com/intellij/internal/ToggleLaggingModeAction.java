// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

final class ToggleLaggingModeAction extends AnAction implements DumbAware {

  private volatile boolean myLagging = false;
  private final Alarm myAlarm = new Alarm();

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    if (myLagging) {
      myLagging = false;
      myAlarm.cancelAllRequests();
    }
    else {
      myLagging = true;
      for (int i = 0; i < 100; i++) {
        new Runnable() {
          @Override
          public void run() {
            DebugUtil.sleep(5);
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(new Task.Backgroundable(null, "lagging") {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                DebugUtil.sleep(1);
              }
            }, new EmptyProgressIndicator());
            if (myLagging) {
              myAlarm.addRequest(this, 1);
            }
          }
        }.run();
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setText(myLagging ?
                                InternalActionsBundle.messagePointer("action.presentation.ToggleLaggingModeAction.text.exit.lagging.mode") :
                                InternalActionsBundle.messagePointer(
                                  "action.presentation.ToggleLaggingModeAction.text.enter.lagging.mode"));
  }
}
