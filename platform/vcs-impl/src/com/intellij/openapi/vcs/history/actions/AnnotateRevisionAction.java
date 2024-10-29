/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.history.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.actions.AnnotateRevisionActionBase;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class AnnotateRevisionAction extends AnnotateRevisionActionBase implements DumbAware {
  public AnnotateRevisionAction() {
    super(VcsBundle.messagePointer("annotate.action.name"),
          VcsBundle.messagePointer("annotate.action.description"),
          AllIcons.Actions.Annotate);
    setShortcutSet(ActionManager.getInstance().getAction("Annotate").getShortcutSet());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  @Override
  protected Editor getEditor(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (filePath == null) return null;
    VirtualFile virtualFile = filePath.getVirtualFile();
    if (virtualFile == null) return null;

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      VirtualFile editorFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
      if (Comparing.equal(editorFile, virtualFile)) return editor;
    }

    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
    if (fileEditor instanceof TextEditor) {
      return ((TextEditor)fileEditor).getEditor();
    }
    return null;
  }

  @Nullable
  @Override
  protected AbstractVcs getVcs(@NotNull AnActionEvent e) {
    return VcsUtil.findVcs(e);
  }

  @Nullable
  @Override
  protected VirtualFile getFile(@NotNull AnActionEvent e) {
    final Boolean nonLocal = e.getData(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION);
    if (Boolean.TRUE.equals(nonLocal)) return null;

    VirtualFile file = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    if (file == null || file.isDirectory()) return null;

    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    if (filePath == null || filePath.getFileType().isBinary()) return null;

    return file;
  }

  @Nullable
  @Override
  protected VcsFileRevision getFileRevision(@NotNull AnActionEvent e) {
    VcsHistorySession historySession = e.getData(VcsDataKeys.HISTORY_SESSION);
    if (historySession == null) return null;

    VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (!historySession.isContentAvailable(revision)) return null;

    return revision;
  }

  @Override
  protected int getAnnotatedLine(@NotNull AnActionEvent e) {
    Editor editor = getEditor(e);
    if (editor == null) return -1;
    return editor.getCaretModel().getLogicalPosition().line;
  }
}
