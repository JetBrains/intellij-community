/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;

import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.DEFAULT_NUM;
import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.MAX_NUM;
import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.*;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.stream.Collectors.toList;

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
    Condition<AnAction> preselectActionCondition = action -> {
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
    List<BranchActionGroup> localBranchActions =
      myMultiRootBranchConfig.getLocalBranchNames().stream().map(l -> createLocalBranchActions(allRepositories, l)).filter(Objects::nonNull)
        .collect(toList());
    wrapWithMoreActionIfNeeded(popupGroup, ContainerUtil.sorted(localBranchActions, FAVORITE_BRANCH_COMPARATOR),
                               getNumOfTopShownBranches(localBranchActions));
    popupGroup.addSeparator("Common Remote Branches");
    List<BranchActionGroup> remoteBranchActions = map(((GitMultiRootBranchConfig)myMultiRootBranchConfig).getRemoteBranches(),
                                                      remoteBranch -> new GitBranchPopupActions.RemoteBranchActions(myProject,
                                                                                                                    allRepositories,
                                                                                                                    remoteBranch,
                                                                                                                    myCurrentRepository));
    wrapWithMoreActionIfNeeded(popupGroup, ContainerUtil.sorted(remoteBranchActions, FAVORITE_BRANCH_COMPARATOR),
                               getNumOfFavorites(remoteBranchActions));
  }

  @Nullable
  private GitBranchPopupActions.LocalBranchActions createLocalBranchActions(@NotNull List<GitRepository> allRepositories,
                                                                            @NotNull String branch) {
    List<GitRepository> repositories = filterRepositoriesNotOnThisBranch(branch, allRepositories);
    return repositories.isEmpty()
           ? null
           : new GitBranchPopupActions.LocalBranchActions(myProject, repositories, branch, myCurrentRepository);
  }

  @NotNull
  @Override
  protected DefaultActionGroup createRepositoriesActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    List<ActionGroup> rootActions = DvcsUtil.sortRepositories(myRepositoryManager.getRepositories()).stream()
      .map(repo -> new RootAction<>(repo, highlightCurrentRepo() ? myCurrentRepository : null,
                                    new GitBranchPopupActions(repo.getProject(), repo).createActions(),
                                    GitBranchUtil.getDisplayableBranchText(repo))).collect(toList());
    wrapWithMoreActionIfNeeded(popupGroup, rootActions, rootActions.size() > MAX_NUM ? DEFAULT_NUM : MAX_NUM);
    return popupGroup;
  }

  @Override
  protected void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup
      .addAll(new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository).createActions(actions, myRepoTitleInfo));
  }
}
