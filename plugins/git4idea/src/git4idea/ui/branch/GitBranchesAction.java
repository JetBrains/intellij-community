/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.ui.branch;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * Invokes a {@link GitBranchPopup} to checkout and control Git branches.
 * </p>
 */
public class GitBranchesAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    GitRepository repository = file == null ?
                               GitBranchUtil.getCurrentRepository(project) :
                               GitBranchUtil.getRepositoryOrGuess(project, file);
    if (repository != null) {
      GitBranchPopup.getInstance(project, repository).asListPopup().showCenteredInCurrentWindow(project);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && !project.isDisposed());
  }
}
