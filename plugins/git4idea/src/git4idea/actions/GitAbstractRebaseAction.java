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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.rebase.GitRebaseActionDialog;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static git4idea.GitUtil.*;

abstract class GitAbstractRebaseAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null || !hasGitRepositories(project)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabledAndVisible(hasRebaseInProgress(project));
    }
  }

  @Override
  public final void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ProgressManager progressManager = ProgressManager.getInstance();
    String progressTitle = getProgressTitle();
    if (getRepositoryManager(project).hasOngoingRebase()) {
      progressManager.run(new Task.Backgroundable(project, progressTitle) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          performActionForProject(project, indicator);
        }
      });
    }
    else {
      final GitRepository repositoryToOperate = chooseRepository(project, GitRebaseUtils.getRebasingRepositories(project));
      if (repositoryToOperate != null) {
        progressManager.run(new Task.Backgroundable(project, progressTitle) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            performActionForRepository(project, repositoryToOperate, indicator);
          }
        });
      }
    }
  }

  @NotNull
  protected abstract String getProgressTitle();

  protected abstract void performActionForProject(@NotNull Project project, @NotNull ProgressIndicator indicator);

  protected abstract void performActionForRepository(@NotNull Project project,
                                                     @NotNull GitRepository repository,
                                                     @NotNull ProgressIndicator indicator);

  private static boolean hasRebaseInProgress(@NotNull Project project) {
    return !GitRebaseUtils.getRebasingRepositories(project).isEmpty();
  }

  @Nullable
  private GitRepository chooseRepository(@NotNull Project project, @NotNull Collection<GitRepository> repositories) {
    GitRepository firstRepo = assertNotNull(ContainerUtil.getFirstItem(repositories));
    if (repositories.size() == 1) return firstRepo;
    ArrayList<VirtualFile> roots = newArrayList(getRootsFromRepositories(repositories));
    GitRebaseActionDialog dialog = new GitRebaseActionDialog(project, getTemplatePresentation().getText(), roots, firstRepo.getRoot());
    dialog.show();
    VirtualFile root = dialog.selectRoot();
    if (root == null) return null;
    return getRepositoryManager(project).getRepositoryForRootQuick(root); // TODO avoid root <-> GitRepository double conversion
  }
}
