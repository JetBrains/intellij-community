/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import org.jetbrains.annotations.NotNull;

public class RefreshRemoteFileAction extends AnAction {
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
