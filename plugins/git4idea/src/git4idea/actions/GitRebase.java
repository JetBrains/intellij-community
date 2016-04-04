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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.rebase.GitRebaseDialog;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.dvcs.DvcsUtil.sortRepositories;
import static git4idea.GitUtil.*;
import static git4idea.rebase.GitRebaseUtils.getRebasingRepositories;
import static java.util.Collections.singletonList;

public class GitRebase extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null || !hasGitRepositories(project)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(getRebasingRepositories(project).size() < getRepositories(project).size());
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ArrayList<GitRepository> repositories = ContainerUtil.newArrayList(getRepositories(project));
    repositories.removeAll(getRebasingRepositories(project));
    List<VirtualFile> roots = ContainerUtil.newArrayList(getRootsFromRepositories(sortRepositories(repositories)));
    VirtualFile defaultRoot = DvcsUtil.guessVcsRoot(project, e.getData(CommonDataKeys.VIRTUAL_FILE));
    final GitRebaseDialog dialog = new GitRebaseDialog(project, roots, defaultRoot);
    if (dialog.showAndGet()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Rebasing...") {
        public void run(@NotNull ProgressIndicator indicator) {
          GitRebaseUtils.rebase(project, singletonList(dialog.getSelectedRepository()), dialog.getSelectedParams(), indicator);
        }
      });
    }
  }
}
