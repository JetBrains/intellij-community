/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * The popup which allows to quickly switch and control Git branches.
 * <p/>
 */
class GitBranchPopup extends DvcsBranchPopup<GitRepository> {

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   *                          In the case of synchronized branch operations current repository matter much less, but sometimes is used,
   *                          for example, it is preselected in the repositories combobox in the compare branches dialog.
   */
  static GitBranchPopup getInstance(@NotNull final Project project, @NotNull GitRepository currentRepository) {
    final GitVcsSettings vcsSettings = GitVcsSettings.getInstance(project);
    Condition<AnAction> preselectActionCondition = new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {

        if (action instanceof GitBranchPopupActions.LocalBranchActions) {
          GitBranchPopupActions.LocalBranchActions branchAction = (GitBranchPopupActions.LocalBranchActions)action;
          String branchName = branchAction.getBranchName();

          String recentBranch;
          List<GitRepository> repositories = branchAction.getRepositories();
          if (repositories.size() == 1) {
            recentBranch = vcsSettings.getRecentBranchesByRepository().get(repositories.iterator().next().getRoot().getPath());
          }
          else {
            recentBranch = vcsSettings.getRecentCommonBranch();
          }

          if (recentBranch != null && recentBranch.equals(branchName)) {
            return true;
          }
        }
        return false;
      }
    };
    return new GitBranchPopup(currentRepository, GitUtil.getRepositoryManager(project), vcsSettings, preselectActionCondition);
  }

  private GitBranchPopup(@NotNull GitRepository currentRepository,
                         @NotNull GitRepositoryManager repositoryManager,
                         @NotNull GitVcsSettings vcsSettings,
                         @NotNull Condition<AnAction> preselectActionCondition) {
    super(currentRepository, repositoryManager, new GitMultiRootBranchConfig(repositoryManager.getRepositories()), vcsSettings,
          preselectActionCondition);
  }

  @Override
  protected void setCurrentBranchInfo() {
    String currentBranchText = "Current branch";
    if (myRepositoryManager.moreThanOneRoot()) {
      if (myMultiRootBranchConfig.diverged()) {
        currentBranchText += " in " + DvcsUtil.getShortRepositoryName(myCurrentRepository) + ": " +
                             GitBranchUtil.getDisplayableBranchText(myCurrentRepository);
      }
      else {
        currentBranchText += ": " + myMultiRootBranchConfig.getCurrentBranch();
      }
    }
    else {
      currentBranchText += ": " + GitBranchUtil.getDisplayableBranchText(myCurrentRepository);
    }
    myPopup.setAdText(currentBranchText, SwingConstants.CENTER);
  }

  @Override
  protected void fillWithCommonRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                 @NotNull AbstractRepositoryManager<GitRepository> repositoryManager) {
    List<GitRepository> allRepositories = repositoryManager.getRepositories();
    popupGroup.add(new GitBranchPopupActions.GitNewBranchAction(myProject, allRepositories));
    popupGroup.add(new GitBranchPopupActions.CheckoutRevisionActions(myProject, allRepositories));

    popupGroup.addAll(createRepositoriesActions());

    popupGroup.addSeparator("Common Local Branches");
    for (String branch : myMultiRootBranchConfig.getLocalBranchNames()) {
      List<GitRepository> repositories = filterRepositoriesNotOnThisBranch(branch, allRepositories);
      if (!repositories.isEmpty()) {
        popupGroup.add(new GitBranchPopupActions.LocalBranchActions(myProject, repositories, branch, myCurrentRepository));
      }
    }

    popupGroup.addSeparator("Common Remote Branches");
    for (String branch : ((GitMultiRootBranchConfig)myMultiRootBranchConfig).getRemoteBranches()) {
      popupGroup.add(new GitBranchPopupActions.RemoteBranchActions(myProject, allRepositories, branch, myCurrentRepository));
    }
  }

  @NotNull
  @Override
  protected DefaultActionGroup createRepositoriesActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    for (GitRepository repository : DvcsUtil.sortRepositories(myRepositoryManager.getRepositories())) {
      popupGroup.add(new RootAction<>(repository, highlightCurrentRepo() ? myCurrentRepository : null,
                                      new GitBranchPopupActions(repository.getProject(), repository).createActions(null),
                                      GitBranchUtil.getDisplayableBranchText(repository)));
    }
    return popupGroup;
  }

  @Override
  protected void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository).createActions(actions));
  }
}
