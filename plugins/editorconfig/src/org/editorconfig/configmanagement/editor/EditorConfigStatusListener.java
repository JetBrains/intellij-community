// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotifications;
import org.editorconfig.Utils;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

public final class EditorConfigStatusListener implements CodeStyleSettingsListener, Disposable {
  private boolean myEnabledStatus;
  private final VirtualFile myVirtualFile;
  private final Project myProject;

  public EditorConfigStatusListener(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    myProject = project;
    myEnabledStatus = Utils.isEnabled(project);
    myVirtualFile = virtualFile;
    CodeStyleSettingsManager.getInstance(project).addListener(this);
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    CodeStyleSettings settings = CodeStyle.getSettings(myProject);
    if (settings.getCustomSettingsIfCreated(EditorConfigSettings.class) == null) {
      // plugin is currently being unloaded, can't run any updates
      return;
    }

    boolean newEnabledStatus = Utils.isEnabled(myProject);
    if (myEnabledStatus != newEnabledStatus) {
      myEnabledStatus = newEnabledStatus;
      onEditorConfigEnabled(newEnabledStatus);
    }
  }

  private void onEditorConfigEnabled(boolean isEnabled) {
    if (!isEnabled) {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
      fileEditorManager.closeFile(myVirtualFile);
      fileEditorManager.openFile(myVirtualFile, false);
    }
    EditorNotifications.getInstance(myProject).updateNotifications(myVirtualFile);
    Document document = FileDocumentManager.getInstance().getDocument(myVirtualFile);
    PsiFile psiFile = document == null ? null : PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile != null) {
      DaemonCodeAnalyzer.getInstance(myProject).restart(psiFile);
    }
  }

  @Override
  public void dispose() {
    CodeStyleSettingsManager.removeListener(myProject, this);
  }
}
