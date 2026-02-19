// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RefreshRemoteFileAction extends AnAction {
  private final HttpVirtualFile myFile;

  public RefreshRemoteFileAction(HttpVirtualFile file) {
    super(IdeBundle.message("action.text.reload.file"), "", AllIcons.Actions.Refresh);
    myFile = file;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final RemoteFileState state = myFile.getFileInfo().getState();
    e.getPresentation().setEnabled(state == RemoteFileState.DOWNLOADED || state == RemoteFileState.ERROR_OCCURRED);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myFile.refresh(true, false);
  }
}
