// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

abstract class CurrentFileTodosPanel extends TodoPanel {

  CurrentFileTodosPanel(@NotNull TodoView todoView,
                        @NotNull TodoPanelSettings settings,
                        @NotNull Content content) {
    super(todoView, settings, true, content);

    VirtualFile[] virtualFiles = FileEditorManager.getInstance(myProject).getSelectedFiles();
    setFile(virtualFiles.length == 0 ? null : ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(virtualFiles[0])), true);
    ClientId clientId = ClientId.getCurrent();
    // It's important to remove this listener. It prevents invocation of setFile method after the tree builder is disposed
    myProject.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent e) {
        if (!clientId.equals(ClientId.getCurrent())) return;
        VirtualFile virtualFile = e.getNewFile();
        PsiFile psiFile = virtualFile == null || !virtualFile.isValid() ? null
                          : ReadAction.compute(() -> PsiManager.getInstance(myProject).findFile(virtualFile));
        // This invokeLater is required. The problem is setFile does a commit to PSI, but setFile is
        // invoked inside PSI change event. It causes an Exception like "Changes to PSI are not allowed inside event processing"
        ApplicationManager.getApplication().invokeLater(() -> setFile(psiFile, false));
      }
    });
  }

  private void setFile(PsiFile file, boolean initialUpdate) {
    // setFile method is invoked in LaterInvocator so PsiManager
    // can be already disposed, so we need to check this before using it.
    if (PsiManager.getInstance(myProject).isDisposed()) {
      return;
    }

    if (file != null && getSelectedFile() == file) return;

    CurrentFileTodosTreeBuilder builder = (CurrentFileTodosTreeBuilder)getTreeBuilder();
    builder.setFile(file);
    if (builder.isUpdatable() || initialUpdate) {
      Object selectableElement = builder.getTodoTreeStructure().getFirstSelectableElement();
      if (selectableElement != null) {
        builder.select(selectableElement);
      }
    }
  }
}
