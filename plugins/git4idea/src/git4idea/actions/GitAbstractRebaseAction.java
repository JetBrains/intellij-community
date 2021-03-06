/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import static git4idea.GitUtil.getRepositoryManager;

public abstract class GitAbstractRebaseAction extends GitOperationActionBase {
  protected GitAbstractRebaseAction() {
    super(Repository.State.REBASING);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (getRepositoryManager(project).hasOngoingRebase()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, getProgressTitle()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          performActionForProject(project, indicator);
        }
      });
    }
    else {
      super.actionPerformed(e);
    }
  }

  @Override
  public void performInBackground(@NotNull GitRepository repositoryToOperate) {
    ProgressManager.getInstance().run(new Task.Backgroundable(repositoryToOperate.getProject(), getProgressTitle()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        performActionForRepository(repositoryToOperate.getProject(), repositoryToOperate, indicator);
      }
    });
  }

  @NotNull
  @Override
  protected String getOperationName() {
    return GitBundle.message("action.Git.Rebase.operation.name");
  }

  @NlsContexts.ProgressTitle
  @NotNull
  protected abstract String getProgressTitle();

  protected abstract void performActionForProject(@NotNull Project project, @NotNull ProgressIndicator indicator);

  protected abstract void performActionForRepository(@NotNull Project project,
                                                     @NotNull GitRepository repository,
                                                     @NotNull ProgressIndicator indicator);
}
