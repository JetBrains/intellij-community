// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class OpenFilesActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (!(fileEditorManager instanceof FileEditorManagerImpl)) {
      return;
    }

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(IdeBundle.message("progress.text.reopening.files"));
    }

    FileEditorManagerImpl manager = (FileEditorManagerImpl)fileEditorManager;
    EditorsSplitters editorSplitters = manager.getMainSplitters();
    Ref<JPanel> panelRef = editorSplitters.restoreEditors();

    ApplicationManager.getApplication().invokeLater(() -> {
      if (panelRef != null) {
        editorSplitters.doOpenFiles(panelRef.get());
      }
      manager.initDockableContentFactory();
      EditorsSplitters.stopOpenFilesActivity(project);
      if (!manager.hasOpenFiles() && !ProjectUtil.isNotificationSilentMode(project)) {
        project.putUserData(FileEditorManagerImpl.NOTHING_WAS_OPENED_ON_START, true);
        if (AdvancedSettings.getBoolean("ide.open.readme.md.on.startup")) {
          RunOnceUtil.runOnceForProject(project, "ShowReadmeOnStart", () -> findAndOpenReadme(project));
        }
      }
    }, project.getDisposed());
  }

  private static void findAndOpenReadme(Project project) {
    VirtualFile dir = ProjectUtil.guessProjectDir(project);
    if (dir != null) {
      VirtualFile readme = dir.findChild("README.md");
      if (readme != null && !readme.isDirectory()) {
        ApplicationManager.getApplication().invokeLater(() -> TextEditorWithPreview.openPreviewForFile(project, readme), project.getDisposed());
      }
    }
  }
}
