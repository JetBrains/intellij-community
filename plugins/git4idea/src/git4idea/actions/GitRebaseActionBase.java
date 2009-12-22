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
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorService;
import git4idea.rebase.GitRebaseLineListener;
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
    GitLineHandler h = createHandler(project, gitRoots, defaultRoot);
    if (h == null) {
      return;
    }
    final VirtualFile root = h.workingDirectoryFile();
    GitRebaseEditorService service = GitRebaseEditorService.getInstance();
    GitInteractiveRebaseEditorHandler editor = new GitInteractiveRebaseEditorHandler(service, project, root, h);
    GitRebaseLineListener resultListener = new GitRebaseLineListener();
    h.addLineListener(resultListener);
    configureEditor(editor);
    affectedRoots.add(root);
    try {
      service.configureHandler(h, editor.getHandlerNo());
      GitHandlerUtil.doSynchronously(h, GitBundle.getString("rebasing.title"), h.printableCommandLine());
    }
    finally {
      editor.close();
      final GitRebaseLineListener.Result result = resultListener.getResult();
      String messageId;
      boolean isError = true;
      switch (result.status) {
        case CONFLICT:
          messageId = "rebase.result.conflict";
          break;
        case ERROR:
          messageId = "rebase.result.error";
          break;
        case CANCELLED:
          isError = false;
          messageId = "rebase.result.cancelled";
          // we do not need to show a message if editing was cancelled.
          exceptions.clear();
          break;
        case EDIT:
          isError = false;
          messageId = "rebase.result.amend";
          break;
        case FINISHED:
        default:
          messageId = null;
      }
      if (messageId != null) {
        String message = GitBundle.message(messageId, result.current, result.total);
        String title = GitBundle.message(messageId + ".title");
        if (isError) {
          Messages.showErrorDialog(project, message, title);
        }
        else {
          Messages.showInfoMessage(project, message, title);
        }
      }
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
