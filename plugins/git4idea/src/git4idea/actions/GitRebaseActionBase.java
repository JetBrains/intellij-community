/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorService;
import git4idea.rebase.GitRebaseLineListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The base class for rebase actions that use editor
 */
public abstract class GitRebaseActionBase extends GitRepositoryAction {
  /**
   * {@inheritDoc}
   */
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    final GitLineHandler h = createHandler(project, gitRoots, defaultRoot);
    if (h == null) {
      return;
    }
    final VirtualFile root = h.workingDirectoryFile();
    GitRebaseEditorService service = GitRebaseEditorService.getInstance();
    final GitInteractiveRebaseEditorHandler editor = new GitInteractiveRebaseEditorHandler(service, project, root, h);
    final GitRebaseLineListener resultListener = new GitRebaseLineListener();
    h.addLineListener(resultListener);
    configureEditor(editor);
    affectedRoots.add(root);

    service.configureHandler(h, editor.getHandlerNo());
    new Task.Backgroundable(project, GitBundle.getString("rebasing.title"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
        try {
          GitCommandResult result = ServiceManager.getService(Git.class).runCommand(h);
          editor.close();
          GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
          manager.updateRepository(root);
          VfsUtil.markDirtyAndRefresh(false, true, false, root);
          notifyAboutResult(result, resultListener, editor.wasNoopSituationDetected(), exceptions, project);
        }
        finally {
          DvcsUtil.workingTreeChangeFinished(project, token);
        }
      }
    }.queue();
  }

  private static void notifyAboutResult(@NotNull GitCommandResult commandResult,
                                        @NotNull GitRebaseLineListener resultListener,
                                        boolean noopSituation,
                                        @NotNull List<VcsException> exceptions,
                                        @NotNull Project project) {
    final GitRebaseLineListener.Result result = resultListener.getResult();
    String messageId;
    String message = null;
    boolean isError = true;
    switch (result.status) {
      case CONFLICT:
        messageId = "rebase.result.conflict";
        break;
      case ERROR:
        messageId = "rebase.result.error";
        message = commandResult.getErrorOutputAsHtmlString();
        break;
      case CANCELLED:
        // we do not need to show a message if editing was cancelled.
        exceptions.clear();
        return;
      case EDIT:
        isError = false;
        messageId = "rebase.result.amend";
        break;
      case FINISHED:
        isError = false;
        messageId = "rebase.result.success";
        if (noopSituation) {
          message = "Current branch was reset to the base branch";
        }
        break;
      default:
        throw new IllegalStateException("Unsupported rebase result: " + result.status);
    }

    String title = GitBundle.message(messageId + ".title");
    if (message == null) {
      message = GitBundle.message(messageId, result.current, result.total);
    }

    if (isError) {
      VcsNotifier.getInstance(project).notifyError(title, message);
    }
    else {
      VcsNotifier.getInstance(project).notifySuccess(title, message);
    }
  }

  /**
   * This method could be overridden to supply additional information to the editor.
   *
   * @param editor the editor to configure
   */
  protected void configureEditor(GitInteractiveRebaseEditorHandler editor) {
  }

  /**
   * Create line handler that represents a git operation
   *
   * @param project     the context project
   * @param gitRoots    the git roots
   * @param defaultRoot the default root
   * @return the line handler or null
   */
  @Nullable
  protected abstract GitLineHandler createHandler(Project project, List<VirtualFile> gitRoots, VirtualFile defaultRoot);
}
