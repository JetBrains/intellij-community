// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ObjectUtils;
import org.editorconfig.Utils;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class EditorConfigStatusListener implements CodeStyleSettingsListener, Disposable {
  private       boolean     myEnabledStatus;
  private final VirtualFile myVirtualFile;
  private final Project     myProject;
  private       Charset     myEncoding;

  private MyReloadTask myReloadTask;

  public EditorConfigStatusListener(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    myProject = project;
    myEnabledStatus = Utils.isEnabled(project);
    myVirtualFile = virtualFile;
    myEncoding = EncodingProjectManager.getInstance(project).getEncoding(virtualFile, true);
    CodeStyleSettingsManager.getInstance(project).addListener(this);
  }

  @Override
  public final void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    boolean newEnabledStatus = Utils.isEnabled(myProject);
    if (myEnabledStatus != newEnabledStatus) {
      myEnabledStatus = newEnabledStatus;
      onEditorConfigEnabled(newEnabledStatus);
    }
    Charset newEncoding = EncodingProjectManager.getInstance(myProject).getEncoding(myVirtualFile, true);
    if (!myEncoding.equals(newEncoding)) {
      onEncodingChanged();
      myEncoding = newEncoding;
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

  private void onEncodingChanged() {
    if (myReloadTask != null) {
      myReloadTask.interrupt();
    }
    MyReloadTask reloadTask = new MyReloadTask();
    ProgressManager.getInstance().run(reloadTask);
    myReloadTask = reloadTask;
  }

  private class MyReloadTask extends Task.Backgroundable {
    private volatile boolean myInterrupted;

    private MyReloadTask() {
      super(EditorConfigStatusListener.this.myProject, EditorConfigBundle.message("encoding.change.reloading.files"), false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      List<VirtualFile> filesToReload = new ArrayList<>();
      VirtualFile parentDir = myVirtualFile.getParent();
      final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VfsUtilCore.visitChildrenRecursively(
        parentDir,
        new VirtualFileVisitor<>() {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            if (myInterrupted) throw new ProcessCanceledException();
            if (!file.isDirectory() && fileDocumentManager.getCachedDocument(file) != null) {
              filesToReload.add(file);
            }
            return true;
          }
        }
      );
      ApplicationManager.getApplication().invokeLater(
        () -> fileDocumentManager.reloadFiles(filesToReload.toArray(VirtualFile.EMPTY_ARRAY)));
    }


    void interrupt() {
      myInterrupted = true;
    }
  }
}
