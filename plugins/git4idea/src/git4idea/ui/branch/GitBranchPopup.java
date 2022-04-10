// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.dvcs.ui.BranchActionGroupPopup;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.dvcs.ui.RootAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.popup.PopupDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.actions.GitFetch;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.config.GitVcsSettings;
import git4idea.fetch.GitFetchResult;
import git4idea.fetch.GitFetchSupport;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseSpec;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Optional;

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
public final class GitBranchPopup extends DvcsBranchPopup<GitRepository> {
  @NonNls static final String DIMENSION_SERVICE_KEY = "Git.Branch.Popup";
  @NonNls static final String SHOW_ALL_LOCALS_KEY = "Git.Branch.Popup.ShowAllLocals";
  @NonNls static final String SHOW_ALL_REMOTES_KEY = "Git.Branch.Popup.ShowAllRemotes";
  @NonNls static final String SHOW_ALL_REPOSITORIES = "Git.Branch.Popup.ShowAllRepositories";
  static final Icon LOADING_ICON = new AnimatedIcon.Default();

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   *                          In the case of synchronized branch operations current repository matter much less, but sometimes is used,
   *                          for example, it is preselected in the repositories combobox in the compare branches dialog.
   */
  public static GitBranchPopup getInstance(@NotNull final Project project, @NotNull GitRepository currentRepository, @NotNull DataContext dataContext) {
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
    return new GitBranchPopup(currentRepository, getRepositoryManager(project), vcsSettings, preselectActionCondition, dataContext);
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
                         @NotNull Condition<AnAction> preselectActionCondition,
                         @NotNull DataContext dataContext) {
    super(currentRepository, repositoryManager, new GitMultiRootBranchConfig(repositoryManager.getRepositories()), vcsSettings,
          preselectActionCondition, DIMENSION_SERVICE_KEY, dataContext);

    final GitBranchIncomingOutgoingManager gitBranchIncomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(myProject);
    if (gitBranchIncomingOutgoingManager.shouldCheckIncoming() && !gitBranchIncomingOutgoingManager.supportsIncomingOutgoing()) {
      myPopup.addToolbarAction(createUnsupportedIncomingAction(myProject), false);
    }
    myPopup.addToolbarAction(createFetchAction(myProject), false);
    MessageBusConnection connection = myProject.getMessageBus().connect(myPopup);
    connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, () -> {
      ApplicationManager.getApplication().invokeLater(() -> myPopup.update(), o -> myPopup.isDisposed());
    });
  }

  @NotNull
  private static AnAction createFetchAction(@NotNull Project project) {
    return new GitFetch() {

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (isBusy(project)) return;
        super.actionPerformed(e);
      }

      @Override
      protected void onFetchFinished(@NotNull GitFetchResult result) {
        GitBranchIncomingOutgoingManager.getInstance(project)
          .forceUpdateBranches(() -> ActivityTracker.getInstance().inc());
        showNotificationIfNeeded(result);
      }

      private void showNotificationIfNeeded(@NotNull GitFetchResult result) {
        Optional<JBPopup> popupOptional =
          StreamEx.of(PopupDispatcher.getInstance().getPopupStream()).findFirst(BranchActionGroupPopup.class::isInstance);
        if (popupOptional.isPresent()) {
          result.showNotificationIfFailed();
        }
        else {
          result.showNotification();
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setIcon(isBusy(project) ? LOADING_ICON : AllIcons.Vcs.Fetch);
        e.getPresentation().setText(isBusy(project) ? GitBundle.message("fetching") : GitBundle.message("action.fetch.text"));
      }

      private boolean isBusy(@NotNull Project project) {
        return GitFetchSupport.fetchSupport(project).isFetchRunning() || GitBranchIncomingOutgoingManager.getInstance(project).isUpdating();
      }
    };
  }

  @NotNull
  private static AnAction createUnsupportedIncomingAction(@NotNull Project project) {
    AnAction updateBranchInfoWithAuthenticationAction = DumbAwareAction.create(
      GitBundle.message("update.checks.not.supported.git.2.9.required"),
      e -> ShowSettingsUtil.getInstance().showSettingsDialog(project, GitBundle.message("settings.git.option.group")));
    Presentation presentation = updateBranchInfoWithAuthenticationAction.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.Warning);
    presentation.setHoveredIcon(AllIcons.General.Warning);
    return updateBranchInfoWithAuthenticationAction;
  }

  @Override
  protected void fillWithCommonRepositoryActions(@NotNull LightActionGroup popupGroup,
                                                 @NotNull AbstractRepositoryManager<GitRepository> repositoryManager) {
    List<GitRepository> allRepositories = repositoryManager.getRepositories();
    GitRebaseSpec rebaseSpec = getRepositoryManager(myProject).getOngoingRebaseSpec();
    // add rebase actions only if sync rebase action is in progress for all repos
    if (rebaseSpec != null && rebaseSpec.getAllRepositories().size() == allRepositories.size()) {
      popupGroup.addAll(GitBranchPopupActions.getRebaseActions());
    }
    popupGroup.add(new GitBranchPopupActions.GitNewBranchAction(myProject, allRepositories));

    if (!ExperimentalUI.isNewVcsBranchPopup()) {
      popupGroup.add(new GitBranchPopupActions.CheckoutRevisionActions(myProject, allRepositories));
    }

    popupGroup.addAll(createRepositoriesActions());

    popupGroup.addSeparator(GitBundle.message("common.local.branches"));
    List<BranchActionGroup> localBranchActions = myMultiRootBranchConfig.getLocalBranchNames().stream()
      .filter(branchName -> !branchName.equals(myMultiRootBranchConfig.getCurrentBranch()))
      .map(l -> createLocalBranchActions(allRepositories, l))
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
    popupGroup.addSeparator(GitBundle.message("common.remote.branches"));

    List<BranchActionGroup> remoteBranchActions = map(((GitMultiRootBranchConfig)myMultiRootBranchConfig).getRemoteBranches(),
                                                      remoteBranch -> new GitBranchPopupActions.RemoteBranchActions(myProject,
                                                                                                                    allRepositories,
                                                                                                                    remoteBranch,
                                                                                                                    myCurrentRepository));
    wrapWithMoreActionIfNeeded(myProject, popupGroup, ContainerUtil.sorted(remoteBranchActions, FAVORITE_BRANCH_COMPARATOR),
                               getNumOfTopShownBranches(remoteBranchActions), SHOW_ALL_REMOTES_KEY);
  }

  @NotNull
  public GitBranchPopupActions.LocalBranchActions createLocalBranchActions(@NotNull List<? extends GitRepository> allRepositories,
                                                                            @NotNull String branch) {
    return new GitBranchPopupActions.LocalBranchActions(myProject, allRepositories, branch, myCurrentRepository);
  }

  @NotNull
  @Override
  protected LightActionGroup createRepositoriesActions() {
    LightActionGroup popupGroup = new LightActionGroup(false);
    popupGroup.addSeparator(GitBundle.message("repositories"));
    List<ActionGroup> rootActions = map(DvcsUtil.sortRepositories(myRepositoryManager.getRepositories()),
                                        repo -> new RootAction<>(repo, new GitBranchPopupActions(repo.getProject(), repo)
                                          .createActions(), getDisplayableBranchText(repo)));
    wrapWithMoreActionIfNeeded(myProject, popupGroup, rootActions, rootActions.size() > MAX_NUM ? DEFAULT_NUM : MAX_NUM,
                               SHOW_ALL_REPOSITORIES);
    return popupGroup;
  }

  @Override
  protected void fillPopupWithCurrentRepositoryActions(@NotNull LightActionGroup popupGroup, @Nullable LightActionGroup actions) {
    GitBranchPopupActions popupActions = new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository);
    ActionGroup actionGroup = popupActions.createActions(actions,
                                                         myInSpecificRepository ? myCurrentRepository : null,
                                                         true);
    popupGroup.addAll(actionGroup);
  }
}
