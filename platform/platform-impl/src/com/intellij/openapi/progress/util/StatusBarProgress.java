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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.ObjectUtils.notNull;

public class StatusBarProgress extends ProgressIndicatorBase {
  // statusBar -> [textToRestore, MyPreviousText]
  private final Map<StatusBar, Pair<String, String>> myStatusBar2SavedText = ContainerUtil.newHashMap();

  public StatusBarProgress() {
    super(true);
  }

  @Override
  public void start() {
    super.start();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {
          if (ApplicationManager.getApplication().isDisposed()) return;
          WindowManager windowManager = WindowManager.getInstance();
          if (windowManager == null) return;

          Project[] projects = ProjectManager.getInstance().getOpenProjects();
          if (projects.length == 0) projects = new Project[]{null};

          for (Project project : projects) {
            StatusBar statusBar = windowManager.getStatusBar(project);
            if (statusBar != null) {
              String info = notNull(statusBar.getInfo(), "");
              myStatusBar2SavedText.put(statusBar, pair(info, info));  // initial value
            }
          }
        }
      }
    );
  }

  @Override
  public void stop() {
    super.stop();
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {
          for (StatusBar statusBar : myStatusBar2SavedText.keySet()) {
            String textToRestore = updateRestoreText(statusBar);
            statusBar.setInfo(textToRestore);
          }
          myStatusBar2SavedText.clear();
        }
      }
    );
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    update();
  }

  @Override
  public void setFraction(double fraction) {
    super.setFraction(fraction);
    update();
  }

  private void update() {
    String text;
    if (!isRunning()) {
      text = "";
    }
    else {
      text = getText();
      double fraction = getFraction();
      if (fraction > 0) {
        text += " " + (int)(fraction * 100 + 0.5) + "%";
      }
    }
    final String _text = text;
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(
      new Runnable() {
        @Override
        public void run() {
          for (StatusBar statusBarEx : myStatusBar2SavedText.keySet()) {
            setStatusBarText(statusBarEx, _text);
          }
        }
      }
    );
  }

  private void setStatusBarText(StatusBar statusBar, String text) {
    updateRestoreText(statusBar);
    Pair<String, String> textsPair = myStatusBar2SavedText.get(statusBar);
    myStatusBar2SavedText.put(statusBar, pair(textsPair.first, text));
    statusBar.setInfo(text);
  }

  private String updateRestoreText(StatusBar statusBar) {
    Pair<String, String> textsPair = myStatusBar2SavedText.get(statusBar);
    // if current status bar info doesn't match the value, that we set, use this value as a restore value
    String info = notNull(statusBar.getInfo(), "");
    if (!textsPair.getSecond().equals(info)) {
      myStatusBar2SavedText.put(statusBar, pair(info, textsPair.second));
    }
    return textsPair.getFirst();
  }
}
