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

import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.RepositoryUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class GitPushAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    Collection<GitRepository> repositories = collectRepositories(project, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    new VcsPushDialog(project, RepositoryUtil.sortRepositories(repositories)).show();
  }

  @NotNull
  private static Collection<GitRepository> collectRepositories(@NotNull Project project, @Nullable VirtualFile[] files) {
    if (files == null) {
      return Collections.singletonList(GitBranchUtil.getCurrentRepository(project));
    }
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    Collection<GitRepository> repositories = ContainerUtil.newHashSet();
    for (VirtualFile file : files) {
      GitRepository repo = manager.getRepositoryForFile(file);
      if (repo != null) {
        repositories.add(repo);
      }
    }
    return repositories;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && !GitUtil.getRepositoryManager(project).getRepositories().isEmpty());
  }
}
