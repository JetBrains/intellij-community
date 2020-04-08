// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.RunOnceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW;

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
      if (!manager.hasOpenFiles()) {
        EditorsSplitters.stopOpenFilesActivity(project);
        if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
          if (Registry.is("ide.open.readme.md.on.startup")) {
            RunOnceUtil.runOnceForProject(project, "ShowReadmeOnStart", () -> findAndOpenReadme(project, manager));
          }
          if (Registry.is("ide.open.project.view.on.startup")) {
            RunOnceUtil.runOnceForProject(project, "OpenProjectViewOnStart", () -> openProjectView(project));
          }
        }
      }
    }, project.getDisposed());
  }

  private static void findAndOpenReadme(@NotNull Project project, @NotNull FileEditorManagerImpl manager) {
    VirtualFile dir = ProjectUtil.guessProjectDir(project);
    if (dir != null) {
      VirtualFile readme = dir.findChild("README.md");
      if (readme != null && !readme.isDirectory()) {
        readme.putUserData(TextEditorWithPreview.DEFAULT_LAYOUT_FOR_FILE, TextEditorWithPreview.Layout.SHOW_PREVIEW);
        ApplicationManager.getApplication().invokeLater(() -> manager.openFile(readme, true), project.getDisposed());
      }
    }
  }

  private static void openProjectView(@NotNull Project project) {
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    manager.invokeLater(() -> {
      if (null == manager.getActiveToolWindowId()) {
        ToolWindow window = manager.getToolWindow(PROJECT_VIEW);
        if (window != null) window.activate(null);
      }
    });
  }
}
