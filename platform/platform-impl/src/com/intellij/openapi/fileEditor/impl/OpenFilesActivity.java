// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

final class OpenFilesActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText("Reopening files...");
    }

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (fileEditorManager instanceof FileEditorManagerImpl) {
      FileEditorManagerImpl manager = (FileEditorManagerImpl)fileEditorManager;
      manager.getMainSplitters().openFiles();
      manager.initDockableContentFactory();
    }
  }
}
