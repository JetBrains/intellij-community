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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUIUtil;
import git4idea.update.GitFetcher;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Git "fetch" action
 */
public class GitFetch extends GitRepositoryAction {
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("fetch.action.name");
  }

  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", false) {
      @Override public void run(@NotNull ProgressIndicator indicator) {
        GitFetcher fetcher = new GitFetcher(project, indicator);
        for (VirtualFile root : gitRoots) {
          fetcher.fetch(root);
        }

        if (!fetcher.isSuccess()) {
          GitVcs instance = GitVcs.getInstance(project);
          if (instance != null && instance.getExecutableValidator().isExecutableValid()) {
            GitUIUtil.notifyMessage(myProject, "", "Fetch failed", NotificationType.ERROR, true, fetcher.getErrors());
          }
        } else {
          GitUIUtil.notifySuccess(project, "", "Fetched successfully");
        }
      }
    });
  }

}
