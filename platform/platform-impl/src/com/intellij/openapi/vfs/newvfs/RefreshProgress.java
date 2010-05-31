/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;

import javax.swing.*;

public class RefreshProgress extends ProgressIndicatorBase {
  private final String myMessage;

  public RefreshProgress(final String message) {
    myMessage = message;
  }

  public void start() {
    super.start();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (ApplicationManager.getApplication().isDisposed()) return;
        final WindowManager windowManager = WindowManager.getInstance();
        if (windowManager == null) return;

        Project[] projects= ProjectManager.getInstance().getOpenProjects();
        if(projects.length==0){
          projects=new Project[]{null};
        }

        for (Project project : projects) {
          final StatusBarEx statusBar = (StatusBarEx) windowManager.getStatusBar(project);
          if (statusBar == null) continue;

          statusBar.startRefreshIndication(myMessage);
        }
      }
    });

  }

  public void stop() {
    super.stop();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (ApplicationManager.getApplication().isDisposed()) return;
        final WindowManager windowManager = WindowManager.getInstance();
        if (windowManager == null) return;

        Project[] projects= ProjectManager.getInstance().getOpenProjects();
        if(projects.length==0){
          projects=new Project[]{null};
        }

        for (Project project : projects) {
          final StatusBarEx statusBar = (StatusBarEx) windowManager.getStatusBar(project);
          if (statusBar == null) continue;

          statusBar.stopRefreshIndication();
        }
      }
    });
  }
}
