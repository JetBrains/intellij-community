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
import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.config.GitVcsSettings;
import git4idea.rebase.GitRebaseSpec;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.DEFAULT_NUM;
import static com.intellij.dvcs.branch.DvcsBranchPopup.MyMoreIndex.MAX_NUM;
import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.intellij.util.containers.ContainerUtil.map;
import static git4idea.GitUtil.getRepositoryManager;
import static git4idea.branch.GitBranchUtil.getDisplayableBranchText;
import static java.util.stream.Collectors.toList;

/**
 * The popup which allows to quickly switch and control Git branches.
 * <p/>
 */
class GitBranchPopup extends DvcsBranchPopup<GitRepository> {
  private static final String DIMENSION_SERVICE_KEY = "Git.Branch.Popup";
  static final String SHOW_ALL_LOCALS_KEY = "Git.Branch.Popup.ShowAllLocals";
  static final String SHOW_ALL_REMOTES_KEY = "Git.Branch.Popup.ShowAllRemotes";
  static final String SHOW_ALL_REPOSITORIES = "Git.Branch.Popup.ShowAllRepositories";

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   *                          In the case of synchronized branch operations current repository matter much less, but sometimes is used,
   *                          for example, it is preselected in the repositories combobox in the compare branches dialog.
   */
  static GitBranchPopup getInstance(@NotNull final Project project, @NotNull GitRepository currentRepository) {
    final GitVcsSettings vcsSettings = GitVcsSettings.getInstance(project);
    Condition<AnAction> preselectActionCondition = action -> {
     GitBranchPopupActions.LocalBranchActions branchAction = getBranchAction(action);
      if (branchAction != null) {
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
    return new GitBranchPopup(currentRepository, getRepositoryManager(project), vcsSettings, preselectActionCondition);
  }

  @Nullable
  private static GitBranchPopupActions.LocalBranchActions getBranchAction(@NotNull AnAction action) {
    AnAction resultAction =
      action instanceof EmptyAction.MyDelegatingActionGroup ? ((EmptyAction.MyDelegatingActionGroup)action).getDelegate() : action;
    return tryCast(resultAction, GitBranchPopupActions.LocalBranchActions.class);
  }

  private GitBranchPopup(@NotNull GitRepository currentRepository,
                         @NotNull GitRepositoryManager repositoryManager,
                         @NotNull GitVcsSettings vcsSettings,
                         @NotNull Condition<AnAction> preselectActionCondition) {
    super(currentRepository, repositoryManager, new GitMultiRootBranchConfig(repositoryManager.getRepositories()), vcsSettings,
          preselectActionCondition, DIMENSION_SERVICE_KEY);

    final GitBranchIncomingOutgoingManager gitBranchIncomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(myProject);
    if (gitBranchIncomingOutgoingManager.hasAuthenticationProblems()) {
      AnAction updateBranchInfoWithAuthenticationAction =
        new DumbAwareAction("Authentication failed. Click to retry", null, AllIcons.General.Warning) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            gitBranchIncomingOutgoingManager.forceUpdateBranches(true);
            myPopup.cancel();
          }
        };
      updateBranchInfoWithAuthenticationAction.getTemplatePresentation().setHoveredIcon(AllIcons.General.Warning);
      myPopup.addToolbarAction(updateBranchInfoWithAuthenticationAction, false);
    }
  }

  @Override
  protected void fillWithCommonRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                 @NotNull AbstractRepositoryManager<GitRepository> repositoryManager) {
    List<GitRepository> allRepositories = repositoryManager.getRepositories();
    GitRebaseSpec rebaseSpec = getRepositoryManager(myProject).getOngoingRebaseSpec();
    // add rebase actions only if sync rebase action is in progress for all repos
    if (rebaseSpec != null && rebaseSpec.getAllRepositories().size() == allRepositories.size()) {
      popupGroup.addAll(GitBranchPopupActions.getRebaseActions());
    }
    popupGroup.add(new GitBranchPopupActions.GitNewBranchAction(myProject, allRepositories));
    popupGroup.add(new GitBranchPopupActions.CheckoutRevisionActions(myProject, allRepositories));

    popupGroup.addAll(createRepositoriesActions());

    popupGroup.addSeparator("Common Local Branches");
    List<BranchActionGroup> localBranchActions = myMultiRootBranchConfig.getLocalBranchNames().stream()
      .map(l -> createLocalBranchActions(allRepositories, l))
      .filter(Objects::nonNull)
      .sorted(FAVORITE_BRANCH_COMPARATOR)
      .collect(toList());
    int topShownBranches = getNumOfTopShownBranches(localBranchActions);
    String currentBranch = MultiRootBranches.getCommonCurrentBranch(allRepositories);
    if (currentBranch != null) {
      localBranchActions
        .add(0, new GitBranchPopupActions.CurrentBranchActions(myProject, allRepositories, currentBranch, myCurrentRepository));
      topShownBranches++;
    }
    wrapWithMoreActionIfNeeded(myProject, popupGroup, localBranchActions, topShownBranches, SHOW_ALL_LOCALS_KEY, true);
    popupGroup.addSeparator("Common Remote Branches");

    List<BranchActionGroup> remoteBranchActions = map(((GitMultiRootBranchConfig)myMultiRootBranchConfig).getRemoteBranches(),
                                                      remoteBranch -> new GitBranchPopupActions.RemoteBranchActions(myProject,
                                                                                                                    allRepositories,
                                                                                                                    remoteBranch,
                                                                                                                    myCurrentRepository));
    wrapWithMoreActionIfNeeded(myProject, popupGroup, ContainerUtil.sorted(remoteBranchActions, FAVORITE_BRANCH_COMPARATOR),
                               getNumOfTopShownBranches(remoteBranchActions), SHOW_ALL_REMOTES_KEY);
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
      .map(
        repo -> new RootAction<>(repo, new GitBranchPopupActions(repo.getProject(), repo).createActions(), getDisplayableBranchText(repo)))
      .collect(toList());
    wrapWithMoreActionIfNeeded(myProject, popupGroup, rootActions, rootActions.size() > MAX_NUM ? DEFAULT_NUM : MAX_NUM,
                               SHOW_ALL_REPOSITORIES);
    return popupGroup;
  }

  @Override
  protected void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(
      new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository).createActions(actions, myRepoTitleInfo, true));
  }
}
