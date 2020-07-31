// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ObjectUtils;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

public class EditorConfigStatusListener implements CodeStyleSettingsListener, Disposable {
  private       boolean     myEnabledStatus;
  private final VirtualFile myVirtualFile;
  private final Project     myProject;

  public EditorConfigStatusListener(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    myProject = project;
    myEnabledStatus = Utils.isEnabled(project);
    myVirtualFile = virtualFile;
    CodeStyleSettingsManager.getInstance(project).addListener(this);
  }

  @Override
  public final void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    boolean newEnabledStatus = Utils.isEnabled(myProject);
    if (myEnabledStatus != newEnabledStatus) {
      myEnabledStatus = newEnabledStatus;
      onEditorConfigEnabled(newEnabledStatus);
    }
  }

  private void onEditorConfigEnabled(boolean isEnabled) {
    if (!isEnabled) {
      FileEditorManager.getInstance(myProject).closeFile(myVirtualFile);
      FileEditorManager.getInstance(myProject).openFile(myVirtualFile, false);
    }
    EditorNotifications.getInstance(myProject).updateNotifications(myVirtualFile);
    ObjectUtils.consumeIfNotNull(
      FileDocumentManager.getInstance().getDocument(myVirtualFile),
      document -> ObjectUtils.consumeIfNotNull(PsiDocumentManager.getInstance(myProject).getPsiFile(document),
                                               psiFile -> DaemonCodeAnalyzer.getInstance(myProject).restart(psiFile)));
  }

  @Override
  public void dispose() {
    CodeStyleSettingsManager.removeListener(myProject, this);
  }
}
