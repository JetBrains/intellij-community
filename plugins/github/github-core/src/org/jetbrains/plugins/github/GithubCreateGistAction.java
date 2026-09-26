// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;

import java.util.ArrayList;
import java.util.List;

public class GithubCreateGistAction extends DumbAwareAction {
  private static final Condition<@Nullable VirtualFile> FILE_WITH_CONTENT = f -> f != null && !(f.getFileType().isBinary());

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    GHAccountManager accountManager = ApplicationManager.getApplication().getService(GHAccountManager.class);
    GHHostedRepositoriesManager hostedRepositoriesManager = project.getService(GHHostedRepositoriesManager.class);
    if (hostedRepositoriesManager.getKnownRepositoriesState().getValue().isEmpty() &&
        accountManager.getAccountsState().getValue().isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    Editor editor = e.getData(CommonDataKeys.EDITOR);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    boolean hasFilesWithContent = FILE_WITH_CONTENT.value(file) || (files != null && ContainerUtil.exists(files, FILE_WITH_CONTENT));
    boolean isTerminal = editor != null && editor.isViewer();
    boolean isDirectory = (file != null && file.isDirectory());

    if (!isTerminal && !isDirectory && (!hasFilesWithContent || editor != null && editor.getDocument().getTextLength() == 0)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      return;
    }

    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    final VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (editor == null && file == null && files == null) {
      return;
    }
    List<VirtualFile> allFiles = new ArrayList<>();

    if (files != null) {
      for (VirtualFile f : files) {
        collectFilesRecursively(f, allFiles);
      }
    }

    GithubCreateGistService service = project.getService(GithubCreateGistService.class);
    service.createGistAction(editor, FILE_WITH_CONTENT.value(file) ? file : null,
                             filterFilesWithContent(allFiles.toArray(VirtualFile.EMPTY_ARRAY)));
  }

  private static void collectFilesRecursively(VirtualFile file, List<VirtualFile> collection) {
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        collectFilesRecursively(child, collection);
      }
    }
    else {
      if (FILE_WITH_CONTENT.value(file)) {
        collection.add(file);
      }
    }
  }

  private static VirtualFile @Nullable [] filterFilesWithContent(@Nullable VirtualFile @Nullable [] files) {
    if (files == null) return null;

    return ContainerUtil.filter(files, FILE_WITH_CONTENT).toArray(VirtualFile.EMPTY_ARRAY);
  }
}