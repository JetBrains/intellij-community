// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.BaseRemoteFileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class HttpFileEditor extends BaseRemoteFileEditor {
  private final RemoteFilePanel myPanel;
  private final @NotNull HttpVirtualFile myFile;

  HttpFileEditor(@NotNull Project project, @NotNull HttpVirtualFile virtualFile) {
    super(project);

    myFile = virtualFile;
    myPanel = new RemoteFilePanel(project, myFile, this);
    RemoteFileInfoImpl fileInfo = (RemoteFileInfoImpl)virtualFile.getFileInfo();
    assert fileInfo != null;
    fileInfo.download()
      .onSuccess(file -> ApplicationManager.getApplication().invokeLater(() -> contentLoaded(), myProject.getDisposed()))
      .onError(throwable -> contentRejected());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getPreferredFocusedComponent();
    }
    return myPanel.getMainPanel();
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public @NotNull String getName() {
    return IdeBundle.message("http.editor.name");
  }

  @Override
  public void selectNotify() {
    myPanel.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myPanel.deselectNotify();
  }

  @Override
  protected @Nullable TextEditor getTextEditor() {
    return myPanel.getFileEditor();
  }

  @Override
  public void dispose() {
    super.dispose();
    myPanel.dispose();
  }
}
