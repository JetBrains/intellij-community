// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts.StatusBarText;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.ObjectUtils.notNull;

/**
 * @deprecated unused in the platform
 */
@Deprecated
public final class StatusBarProgress extends ProgressIndicatorBase {
  // statusBar -> [textToRestore, MyPreviousText]
  private final Map<StatusBar, Pair<@StatusBarText String, @StatusBarText String>> myStatusBar2SavedText = new HashMap<>();
  private boolean myScheduledStatusBarTextSave;

  public StatusBarProgress() {
    super(true);
  }

  @Override
  public void start() {
    myScheduledStatusBarTextSave = false;
    super.start();
  }

  @Override
  public void stop() {
    super.stop();

    if (myScheduledStatusBarTextSave) {
      SwingUtilities.invokeLater(
        () -> {
          for (StatusBar statusBar : myStatusBar2SavedText.keySet()) {
            String textToRestore = updateRestoreText(statusBar);
            statusBar.setInfo(textToRestore);
          }
          myStatusBar2SavedText.clear();
        }
      );
    }
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

    String _text = text;
    if (!myScheduledStatusBarTextSave) {
      myScheduledStatusBarTextSave = true;
      SwingUtilities.invokeLater(
        () -> {
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
      );
    }

    SwingUtilities.invokeLater(
      () -> {
        for (StatusBar statusBarEx : myStatusBar2SavedText.keySet()) {
          setStatusBarText(statusBarEx, _text);
        }
      }
    );
  }

  private void setStatusBarText(StatusBar statusBar, @StatusBarText String text) {
    updateRestoreText(statusBar);
    Pair<@StatusBarText String, @StatusBarText String> textsPair = myStatusBar2SavedText.get(statusBar);
    myStatusBar2SavedText.put(statusBar, pair(textsPair.first, text));
    statusBar.setInfo(text);
  }

  private @StatusBarText String updateRestoreText(StatusBar statusBar) {
    Pair<@StatusBarText String, @StatusBarText String> textsPair = myStatusBar2SavedText.get(statusBar);
    // if current status bar info doesn't match the value, that we set, use this value as a restore value
    String info = notNull(statusBar.getInfo(), "");
    if (!textsPair.second.equals(info)) {
      myStatusBar2SavedText.put(statusBar, pair(info, textsPair.second));
    }
    return textsPair.first;
  }
}
