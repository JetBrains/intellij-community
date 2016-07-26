/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import static org.jetbrains.plugins.github.util.GithubUtil.LOG;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubOpenInBrowserAction extends DumbAwareAction {
  public static final String CANNOT_OPEN_IN_BROWSER = "Can't open in browser";

  protected GithubOpenInBrowserAction() {
    super("Open file on GitHub", "Open selected file in browser", GithubIcons.Github_icon);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || project.isDefault() || virtualFile == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    GitRepository gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFile(virtualFile);
    if (gitRepository == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isUnversioned(virtualFile)) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }

    Change change = changeListManager.getChange(virtualFile);
    if (change != null && change.getType() == Change.Type.NEW) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (virtualFile == null || project == null || project.isDisposed()) {
      return;
    }

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(virtualFile);
    assert repository != null;

    String githubRemoteUrl = GithubUtil.findGithubRemoteUrl(repository);
    if (githubRemoteUrl == null) {
      GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, "Can't find github remote");
      return;
    }

    String relativePath = VfsUtilCore.getRelativePath(virtualFile, repository.getRoot());
    if (relativePath == null) {
      GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, "File is not under repository root",
                                    "Root: " + repository.getRoot().getPresentableUrl() + ", file: " + virtualFile.getPresentableUrl());
      return;
    }

    String hash = getCurrentFileRevisionHash(project, virtualFile);
    if (hash == null) {
      GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, "Can't get last revision.");
      return;
    }

    String url = makeUrlToOpen(editor, relativePath, hash, githubRemoteUrl);
    if (url != null) {
      BrowserUtil.browse(url);
    }
  }

  @Nullable
  private static String makeUrlToOpen(@Nullable Editor editor,
                                      @NotNull String relativePath,
                                      @NotNull String branch,
                                      @NotNull String githubRemoteUrl) {
    StringBuilder builder = new StringBuilder();
    String githubRepoUrl = GithubUrlUtil.makeGithubRepoUrlFromRemoteUrl(githubRemoteUrl);
    if (githubRepoUrl == null) return null;

    if (StringUtil.isEmptyOrSpaces(relativePath)) {
      builder.append(githubRepoUrl).append("/tree/").append(branch);
    }
    else {
      builder.append(githubRepoUrl).append("/blob/").append(branch).append('/').append(relativePath);
    }

    if (editor != null && editor.getDocument().getLineCount() >= 1) {
      // lines are counted internally from 0, but from 1 on github
      SelectionModel selectionModel = editor.getSelectionModel();
      final int begin = editor.getDocument().getLineNumber(selectionModel.getSelectionStart()) + 1;
      final int selectionEnd = selectionModel.getSelectionEnd();
      int end = editor.getDocument().getLineNumber(selectionEnd) + 1;
      if (editor.getDocument().getLineStartOffset(end - 1) == selectionEnd) {
        end -= 1;
      }
      builder.append("#L").append(begin).append("-L").append(end);
    }

    return builder.toString();
  }

  @Nullable
  private static String getCurrentFileRevisionHash(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Ref<GitRevisionNumber> ref = new Ref<>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Getting Last Revision", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          ref.set((GitRevisionNumber)GitHistoryUtils.getCurrentRevision(project, VcsUtil.getFilePath(file), "HEAD"));
        }
        catch (VcsException e) {
          LOG.warn(e);
        }
      }

      @Override
      public void onCancel() {
        throw new ProcessCanceledException();
      }
    });
    if (ref.isNull()) return null;
    return ref.get().getRev();
  }
}