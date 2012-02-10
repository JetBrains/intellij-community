/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.WizardPopup;
import com.intellij.ui.popup.list.ListPopupImpl;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.List;

/**
 * <p>
 *   The popup which allows to quickly switch and control Git branches.
 * </p>
 * <p>
 *   Use {@link #asListPopup()} to achieve the {@link ListPopup} itself.
 * </p>
 * 
 * @author Kirill Likhodedov
 */
class GitBranchPopup  {

  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;
  private final GitVcsSettings myVcsSettings;
  private final GitVcs myVcs;
  private final GitMultiRootBranchConfig myMultiRootBranchConfig;

  private final GitRepository myCurrentRepository;
  private final ListPopupImpl myPopup;

  ListPopup asListPopup() {
    return myPopup;
  }

  /**
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   *                          In the case of synchronized branch operations current repository matter much less, but sometimes is used,
   *                          for example, it is preselected in the repositories combobox in the compare branches dialog.
   */
  static GitBranchPopup getInstance(@NotNull Project project, @NotNull GitRepository currentRepository) {
    return new GitBranchPopup(project, currentRepository);
  }

  private GitBranchPopup(@NotNull Project project, @NotNull GitRepository currentRepository) {
    myProject = project;
    myCurrentRepository = currentRepository;
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myVcs = GitVcs.getInstance(project);
    myVcsSettings = GitVcsSettings.getInstance(myProject);

    myMultiRootBranchConfig = new GitMultiRootBranchConfig(myRepositoryManager.getRepositories());

    String title = createPopupTitle(currentRepository);

    Condition<AnAction> preselectActionCondition = new Condition<AnAction>() {
      @Override
      public boolean value(AnAction action) {
        if (action instanceof GitBranchPopupActions.LocalBranchActions) {
          GitBranchPopupActions.LocalBranchActions branchAction = (GitBranchPopupActions.LocalBranchActions)action;
          String branchName = branchAction.getBranchName();

          String recentBranch;
          List<GitRepository> repositories = branchAction.getRepositories();
          if (repositories.size() == 1) {
            recentBranch = myVcsSettings.getRecentBranchesByRepository().get(repositories.iterator().next().getRoot().getPath());
          }
          else {
            recentBranch = myVcsSettings.getRecentCommonBranch();
          }

          if (recentBranch != null && recentBranch.equals(branchName)) {
            return true;
          }
        }
        return false;
      }
    };
    myPopup = new PopupFactoryImpl.ActionGroupPopup(
      title, createActions(),
      SimpleDataContext.getProjectContext(project),
      false, false, false, true, null, -1, preselectActionCondition, null) {
      @Override
      protected WizardPopup createPopup(WizardPopup parent, PopupStep step, Object parentValue) {
        WizardPopup popup = super.createPopup(parent, step, parentValue);
        if (parentValue instanceof PopupFactoryImpl.ActionItem) {
          AnAction action = ((PopupFactoryImpl.ActionItem)parentValue).getAction();
          if (action instanceof RootAction) {
            popup.setAdText(((RootAction)action).getCaption());
          }
        }
        return popup;
      }
    };

    initBranchSyncPolicyIfNotInitialized();
    setCurrentBranchInfo();
    warnThatBranchesDivergedIfNeeded();
  }

  private void initBranchSyncPolicyIfNotInitialized() {
    if (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == GitBranchSyncSetting.NOT_DECIDED) {
      if (!myMultiRootBranchConfig.diverged()) {
        notifyAboutSyncedBranches();
        myVcsSettings.setSyncSetting(GitBranchSyncSetting.SYNC);
      }
      else {
        myVcsSettings.setSyncSetting(GitBranchSyncSetting.DONT);
      }
    }
  }

  @NotNull
  private String createPopupTitle(@NotNull GitRepository currentRepository) {
    String title = "Git Branches";
    if (myRepositoryManager.moreThanOneRoot() &&
        (myMultiRootBranchConfig.diverged() || myVcsSettings.getSyncSetting() == GitBranchSyncSetting.DONT)) {
      title += " in " + GitUIUtil.getShortRepositoryName(currentRepository);
    }
    return title;
  }

  private void setCurrentBranchInfo() {
    String currentBranchText = "Current branch";
    if (myRepositoryManager.moreThanOneRoot()) {
      if (myMultiRootBranchConfig.diverged()) {
        currentBranchText += " in " + GitUIUtil.getShortRepositoryName(myCurrentRepository) + ": " +
                             GitBranchUiUtil.getDisplayableBranchText(myCurrentRepository);
      }
      else {
        currentBranchText += ": " + myMultiRootBranchConfig.getCurrentBranch();
      }
    }
    else {
      currentBranchText += ": " + GitBranchUiUtil.getDisplayableBranchText(myCurrentRepository);
    }
    myPopup.setAdText(currentBranchText, SwingConstants.CENTER);
  }

  private void notifyAboutSyncedBranches() {
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Synchronous branch control enabled",
      "You have several Git roots in the project and they all are checked out at the same branch. " +
      "We've enabled synchronous branch control for the project. <br/>" +
      "If you wish to control branches in different roots separately, you may <a href='settings'>disable</a> the setting.",
      NotificationType.INFORMATION, new NotificationListener() {
      @Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, myVcs.getConfigurable().getDisplayName());
          if (myVcsSettings.getSyncSetting() == GitBranchSyncSetting.DONT) {
            notification.expire();
          }
        }
      }
    }).notify(myProject);
  }

  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(myProject);
    if (repositoryManager.moreThanOneRoot()) {

      if (!myMultiRootBranchConfig.diverged() && userWantsSyncControl()) {
        fillWithCommonRepositoryActions(popupGroup, repositoryManager);
      }
      else {
        fillPopupWithCurrentRepositoryActions(popupGroup, createRepositoriesActions());
      }
    } 
    else {
      fillPopupWithCurrentRepositoryActions(popupGroup, null);
    }

    popupGroup.addSeparator();
    return popupGroup;
  }

  private boolean userWantsSyncControl() {
    return (myVcsSettings.getSyncSetting() != GitBranchSyncSetting.DONT);
  }

  private void fillWithCommonRepositoryActions(DefaultActionGroup popupGroup, GitRepositoryManager repositoryManager) {
    List<GitRepository> repositories = repositoryManager.getRepositories();
    String currentBranch = myMultiRootBranchConfig.getCurrentBranch();
    assert currentBranch != null : "Current branch can't be null if branches have not diverged";
    popupGroup.add(new GitBranchPopupActions.NewBranchAction(myProject, repositories, myCurrentRepository));

    popupGroup.addAll(createRepositoriesActions());

    popupGroup.addSeparator("Common Local Branches");
    for (String branch : myMultiRootBranchConfig.getLocalBranches()) {
      if (!branch.equals(currentBranch)) {
        popupGroup.add(new GitBranchPopupActions.LocalBranchActions(myProject, repositories, branch, myCurrentRepository));
      }
    }

    popupGroup.addSeparator("Common Remote Branches");
    for (String branch : myMultiRootBranchConfig.getRemoteBranches()) {
      popupGroup.add(new GitBranchPopupActions.RemoteBranchActions(myProject, repositories, branch, myCurrentRepository));
    }
  }

  private void warnThatBranchesDivergedIfNeeded() {
    if (myMultiRootBranchConfig.diverged() && userWantsSyncControl()) {
      myPopup.setWarning("Branches have diverged");
    }
  }

  private DefaultActionGroup createRepositoriesActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addSeparator("Repositories");
    for (GitRepository repository : GitRepositoryManager.getInstance(myProject).getRepositories()) {
      popupGroup.add(new RootAction(repository, highlightCurrentRepo() ? myCurrentRepository : null));
    }
    return popupGroup;
  }

  private boolean highlightCurrentRepo() {
    return !userWantsSyncControl() || myMultiRootBranchConfig.diverged();
  }

  private void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    popupGroup.addAll(new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository).createActions(actions));
  }

  private static class RootAction extends ActionGroup {

    private final GitRepository myRepository;

    /**
     * @param currentRepository Pass null in the case of common repositories - none repository will be highlighted then.
     */
    RootAction(@NotNull GitRepository repository, @Nullable GitRepository currentRepository) {
      super(GitUIUtil.getShortRepositoryName(repository), true);
      myRepository = repository;
      if (repository.equals(currentRepository)) {
        getTemplatePresentation().setIcon(IconLoader.getIcon("/actions/checked.png"));
      }
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      ActionGroup group = new GitBranchPopupActions(myRepository.getProject(), myRepository).createActions(null);
      return group.getChildren(e);
    }

    @NotNull
    public String getCaption() {
      return "Current branch in " + GitUIUtil.getShortRepositoryName(myRepository) + ": " +
             GitBranchUiUtil.getDisplayableBranchText(myRepository);
    }
  }

}
