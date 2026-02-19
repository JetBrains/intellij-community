// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.impl.VcsSelectionHistoryDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsSelectionUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SelectedBlockHistoryAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    assert project != null;

    final VcsSelection selection = VcsSelectionUtil.getSelection(this, event);
    assert selection != null;

    showHistoryForSelection(selection, project);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    Project project = event.getData(CommonDataKeys.PROJECT);
    VcsSelection selection = VcsSelectionUtil.getSelection(this, event);

    presentation.setEnabled(isEnabled(project, selection));
    if (selection != null) {
      presentation.setText(selection.getActionName());
    }
  }

  public static boolean isEnabled(@Nullable Project project, @Nullable VcsSelection selection) {
    if (project == null || selection == null) return false;

    VirtualFile file = FileDocumentManager.getInstance().getFile(selection.getDocument());
    if (file == null) return false;
    FilePath filePath = VcsUtil.getFilePath(file);

    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (activeVcs == null) return false;

    VcsHistoryProvider provider = activeVcs.getVcsBlockHistoryProvider();
    if (provider == null) return false;

    if (!AbstractVcs.fileInVcsByFileStatus(project, filePath)) return false;
    return true;
  }

  public static void showHistoryForSelection(VcsSelection selection, Project project) {
    final VirtualFile file = FileDocumentManager.getInstance().getFile(selection.getDocument());
    assert file != null;

    final AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    assert activeVcs != null;

    final VcsHistoryProvider provider = activeVcs.getVcsBlockHistoryProvider();
    assert provider != null;

    final int selectionStart = selection.getSelectionStartLineNumber();
    final int selectionEnd = selection.getSelectionEndLineNumber();

    VcsSelectionHistoryDialog dialog = new VcsSelectionHistoryDialog(project,
                                                                     file,
                                                                     selection.getDocument(),
                                                                     provider,
                                                                     activeVcs,
                                                                     Math.min(selectionStart, selectionEnd),
                                                                     Math.max(selectionStart, selectionEnd),
                                                                     selection.getDialogTitle());
    dialog.show();
  }
}
