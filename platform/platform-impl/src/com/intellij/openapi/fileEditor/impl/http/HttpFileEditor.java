// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.BaseRemoteFileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
class HttpFileEditor extends BaseRemoteFileEditor {
  private final RemoteFilePanel myPanel;

  public HttpFileEditor(@NotNull Project project, @NotNull HttpVirtualFile virtualFile) {
    super(project);

    myPanel = new RemoteFilePanel(project, virtualFile, this);
    RemoteFileInfoImpl fileInfo = (RemoteFileInfoImpl)virtualFile.getFileInfo();
    assert fileInfo != null;
    fileInfo.download()
      .onSuccess(file -> ApplicationManager.getApplication().invokeLater(() -> contentLoaded(), myProject.getDisposed()))
      .onError(throwable -> contentRejected());
  }

  @Override
  @NotNull
  public JComponent getComponent() {
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
  @NotNull
  public String getName() {
    return "Http";
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
  @Nullable
  protected TextEditor getTextEditor() {
    return myPanel.getFileEditor();
  }

  @Override
  public void dispose() {
    super.dispose();
    myPanel.dispose();
  }
}
