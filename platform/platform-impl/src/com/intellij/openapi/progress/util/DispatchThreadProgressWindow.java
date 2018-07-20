/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
