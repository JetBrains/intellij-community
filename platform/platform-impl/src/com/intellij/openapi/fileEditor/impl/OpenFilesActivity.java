// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class OpenFilesActivity implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (!(fileEditorManager instanceof FileEditorManagerImpl)) {
      return;
    }

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText("Reopening files...");
    }

    FileEditorManagerImpl manager = (FileEditorManagerImpl)fileEditorManager;
    manager.getMainSplitters().openFiles();
    manager.initDockableContentFactory();

    if (manager.getOpenFiles().length == 0 && 
        !ApplicationManager.getApplication().isHeadlessEnvironment() && 
        Registry.is("ide.open.readme.md.on.startup")) {
      RunOnceUtil.runOnceForProject(project, "ShowReadmeOnStart",
                                    () -> findAndOpenReadme(project, manager));
    }
  }

  private static void findAndOpenReadme(@NotNull Project project, FileEditorManagerImpl manager) {
    VirtualFile dir = ProjectUtil.guessProjectDir(project);
    if (dir != null) {
      VirtualFile readme = dir.findChild("README.md");
      if (readme != null && !readme.isDirectory()) {
        readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, TextEditorWithPreview.Layout.SHOW_PREVIEW);
        ApplicationManager.getApplication().invokeLater(() -> manager.openFile(readme, true), project.getDisposed());
      }
    }
  }
}
