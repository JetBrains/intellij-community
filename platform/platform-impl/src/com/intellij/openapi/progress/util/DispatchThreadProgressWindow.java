// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

public class DispatchThreadProgressWindow extends ProgressWindow{
  private long myLastPumpEventsTime;
  private static final int PUMP_INTERVAL = SystemInfo.isWindows ? 100 : 500;
  private Runnable myRunnable;

  public DispatchThreadProgressWindow(boolean shouldShowCancel, Project project) {
    super(shouldShowCancel, project);
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    pumpEvents();
  }

  @Override
  public void setFraction(double fraction) {
    super.setFraction(fraction);
    pumpEvents();
  }

  @Override
  public void setText2(String text) {
    super.setText2(text);
    pumpEvents();
  }

  private void pumpEvents() {
    long time = System.currentTimeMillis();
    if (time - myLastPumpEventsTime < PUMP_INTERVAL) return;
    myLastPumpEventsTime = time;

    IdeEventQueue.getInstance().flushQueue();
  }

  @Override
  protected void prepareShowDialog() {
    if (myRunnable != null) {
      ApplicationManager.getApplication().invokeLater(myRunnable, getModalityState());
    }
    showDialog();
  }

  public void setRunnable(final Runnable runnable) {
    myRunnable = runnable;
  }
}
