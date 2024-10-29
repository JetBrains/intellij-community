// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionExtensionProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.actions.GetVersionAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class GetVersionFromRepositoryActionProvider implements AnActionExtensionProvider {
  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS) != null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
    if (changes == null) return;

    boolean isEnabled = project != null && changes.length > 0;
    e.getPresentation().setEnabledAndVisible(isEnabled);
    e.getPresentation().setText(VcsBundle.message("action.name.get.file.content.from.repository"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    Change[] changes = e.getData(VcsDataKeys.SELECTED_CHANGES_IN_DETAILS);
    if (changes == null) return;
    List<GetVersionAction.FileRevisionProvider> fileContentProviders = ContainerUtil.map(changes, change -> {
      return new MyFileContentProvider(change);
    });
    GetVersionAction.doGet(project, VcsBundle.message("activity.name.get"), fileContentProviders, null);
  }

  private static class MyFileContentProvider implements GetVersionAction.FileRevisionProvider {
    @NotNull private final Change myChange;

    private MyFileContentProvider(@NotNull Change change) {
      myChange = change;
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return ChangesUtil.getFilePath(myChange);
    }

    @Override
    public @Nullable GetVersionAction.FileRevisionContent getContent() throws VcsException {
      ContentRevision revision = myChange.getAfterRevision();
      if (revision == null) return null;

      byte[] bytes = ChangesUtil.loadContentRevision(revision);
      return new GetVersionAction.FileRevisionContent(bytes, null);
    }
  }
}
