/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.Alarm;
import com.intellij.util.TimeoutUtil;

import java.util.Arrays;

/**
 * @author peter
 */
public class ToggleLaggingModeAction extends AnAction implements DumbAware {
  private volatile boolean myLagging = false;
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD); 

  public void actionPerformed(final AnActionEvent e) {
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
            myAlarm.addRequest(this, 1);
          }
        }.run();
      }
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    presentation.setEnabled(project != null && myLagging == DumbServiceImpl.getInstance(project).isDumb());
    if (myLagging) {
      presentation.setText("Exit lagging mode");
    }
    else {
      presentation.setText("Enter lagging mode");
    }
  }
}
