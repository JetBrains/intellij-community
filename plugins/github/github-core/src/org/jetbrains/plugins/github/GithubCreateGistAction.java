// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.i18n.GithubBundle;
import org.jetbrains.plugins.github.ui.GithubCreateGistDialog;
import org.jetbrains.plugins.github.util.*;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

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

    if (!hasFilesWithContent || editor != null && editor.getDocument().getTextLength() == 0) {
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

    createGistAction(project, editor, FILE_WITH_CONTENT.value(file) ? file : null, filterFilesWithContent(files));
  }

  private static VirtualFile @Nullable [] filterFilesWithContent(@Nullable VirtualFile @Nullable [] files) {
    if (files == null) return null;

    return ContainerUtil.filter(files, FILE_WITH_CONTENT).toArray(VirtualFile.EMPTY_ARRAY);
  }

  private static void createGistAction(final @NotNull Project project,
                                       final @Nullable Editor editor,
                                       final @Nullable VirtualFile file,
                                       final VirtualFile @Nullable [] files) {
    GithubSettings settings = GithubSettings.getInstance();
    // Ask for description and other params
    @Nullable String fileName = GithubGistContentsCollector.Companion.getGistFileName(editor, files);
    GithubCreateGistDialog dialog = new GithubCreateGistDialog(project,
                                                               fileName,
                                                               settings.isPrivateGist(),
                                                               settings.isOpenInBrowserGist(),
                                                               settings.isCopyURLGist());
    if (!dialog.showAndGet()) {
      return;
    }
    settings.setPrivateGist(dialog.isSecret());
    settings.setOpenInBrowserGist(dialog.isOpenInBrowser());
    settings.setCopyURLGist(dialog.isCopyURL());

    GithubAccount account = requireNonNull(dialog.getAccount());

    final Ref<String> url = new Ref<>();
    new Task.Backgroundable(project, GithubBundle.message("create.gist.process")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String token = GHCompatibilityUtil.getOrRequestToken(account, project);
        if (token == null) return;
        GithubApiRequestExecutor requestExecutor = GithubApiRequestExecutor.Factory.getInstance().create(account.getServer(), token);

        List<FileContent> contents = GithubGistContentsCollector.Companion.collectContents(project, editor, file, files);
        if (contents.isEmpty()) return;

        String gistUrl = createGist(project, requestExecutor, indicator, account.getServer(),
                                    contents, dialog.isSecret(), dialog.getDescription(), dialog.getFileName());
        url.set(gistUrl);
      }

      @Override
      public void onSuccess() {
        if (url.isNull()) {
          return;
        }
        if (dialog.isCopyURL()) {
          StringSelection stringSelection = new StringSelection(url.get());
          CopyPasteManager.getInstance().setContents(stringSelection);
        }
        if (dialog.isOpenInBrowser()) {
          BrowserUtil.browse(url.get());
        }
        else {
          GithubNotifications
            .showInfoURL(project,
                         GithubNotificationIdsHolder.GIST_CREATED,
                         GithubBundle.message("create.gist.success"),
                         GithubBundle.message("create.gist.url"), url.get());
        }
      }
    }.queue();
  }

  @VisibleForTesting
  public static @Nullable String createGist(@NotNull Project project,
                                            @NotNull GithubApiRequestExecutor executor,
                                            @NotNull ProgressIndicator indicator,
                                            @NotNull GithubServerPath server,
                                            @NotNull List<? extends FileContent> contents,
                                            final boolean isSecret,
                                            final @NotNull String description,
                                            @Nullable String filename) {
    if (contents.isEmpty()) {
      GithubNotifications.showWarning(project,
                                      GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
                                      GithubBundle.message("cannot.create.gist"),
                                      GithubBundle.message("create.gist.error.empty"));
      return null;
    }
    if (contents.size() == 1 && filename != null) {
      FileContent entry = contents.iterator().next();
      contents = Collections.singletonList(new FileContent(filename, entry.getContent()));
    }
    try {
      return executor.execute(indicator, GithubApiRequests.Gists.create(server, contents, description, !isSecret)).getHtmlUrl();
    }
    catch (IOException e) {
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.GIST_CANNOT_CREATE,
                                    GithubBundle.message("cannot.create.gist"),
                                    e);
      return null;
    }
  }
}