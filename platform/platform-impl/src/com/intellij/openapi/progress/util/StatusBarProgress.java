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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.util.containers.HashMap;

import javax.swing.*;

public class StatusBarProgress extends ProgressIndicatorBase {
  // statusBar -> [textToRestore, MyPreviousText]
  private final HashMap<StatusBarEx, Pair<String, String>> myStatusBar2SavedText = new HashMap<StatusBarEx, Pair<String, String>>();

  public StatusBarProgress() {
    super(true);
  }

  @Override
  public void start() {
    super.start();
    SwingUtilities.invokeLater (
      new Runnable() {
        @Override
        public void run() {
          if (ApplicationManager.getApplication().isDisposed()) return;
          final WindowManager windowManager = WindowManager.getInstance();
          if (windowManager == null) return;

          Project[] projects=ProjectManager.getInstance().getOpenProjects();
          if(projects.length==0){
            projects=new Project[]{null};
          }

          for (Project project : projects) {
            final StatusBarEx statusBar = (StatusBarEx)windowManager.getStatusBar(project);
            if (statusBar == null) continue;

            String info = statusBar.getInfo();
            if (info == null) info = "";
            myStatusBar2SavedText.put(statusBar, new Pair<String, String>(info, info)); // initial value
          }
        }
      }
    );
  }

  @Override
  public void stop() {
    super.stop();
    SwingUtilities.invokeLater (
      new Runnable() {
        @Override
        public void run() {
          for (final StatusBarEx statusBar : myStatusBar2SavedText.keySet()) {
            final String textToRestore = updateRestoreText(statusBar);
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

  private void update(){
    String text;
    if (!isRunning()){
      text = "";
    }
    else{
      text = getText();
      double fraction = getFraction();
      if (fraction > 0) {
        text += " " + (int)(fraction * 100 + 0.5) + "%";
      }
    }
    final String text1 = text;
    SwingUtilities.invokeLater (
      new Runnable() {
        @Override
        public void run() {
          for (final StatusBarEx statusBarEx : myStatusBar2SavedText.keySet()) {
            setStatusBarText(statusBarEx, text1);
          }
        }
      }
    );
  }

  private void setStatusBarText(StatusBarEx statusBar, String text) {
    updateRestoreText(statusBar);
    final Pair<String, String> textsPair = myStatusBar2SavedText.get(statusBar);
    myStatusBar2SavedText.put(statusBar, Pair.create(textsPair.first, text));
    statusBar.setInfo(text);
  }

  private String updateRestoreText(StatusBarEx statusBar) {
    final Pair<String, String> textsPair = myStatusBar2SavedText.get(statusBar);
    // if current status bar info doesn't match the value, that we set, use this value as a restore value
    String info = statusBar.getInfo();
    if (info == null) {
      info = "";
    }
    if (!textsPair.getSecond().equals(info)) {
      myStatusBar2SavedText.put(statusBar, Pair.create(info, textsPair.second));
    }
    return textsPair.getFirst();
  }
}
