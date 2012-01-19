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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;

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

  private static final Logger LOG = Logger.getInstance(GitBranchPopup.class);
  
  private final Project myProject;
  private final GitRepository myCurrentRepository;
  private final ListPopup myPopup;
  private GitMultiRootBranchConfig myMultiRootBranchConfig;

  ListPopup asListPopup() {
    return myPopup;
  }

  /**
   *
   * @param project
   * @param currentRepository Current repository, which means the repository of the currently open or selected file.
   *                          In the case of synchronized branch operations current repository matter much less, but sometimes is used,
   *                          for example, it is preselected in the repositories combobox in the compare branches dialog.
   * @return
   */
  static GitBranchPopup getInstance(@NotNull Project project, @NotNull GitRepository currentRepository) {
    return new GitBranchPopup(project, currentRepository);
  }

  private GitBranchPopup(@NotNull Project project, @NotNull GitRepository currentRepository) {
    myProject = project;
    myCurrentRepository = currentRepository;

    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    myMultiRootBranchConfig = new GitMultiRootBranchConfig(repositoryManager.getRepositories());
    
    String title = "Git Branches";
    if (repositoryManager.moreThanOneRoot() && (myMultiRootBranchConfig.diverged() || getSyncSetting() == GitBranchSyncSetting.DONT)) {
      title += " on [" + GitUIUtil.getShortRepositoryName(currentRepository) + "]";
    }

    myPopup = JBPopupFactory.getInstance().createActionGroupPopup(
      title, createActions(),
      SimpleDataContext.getProjectContext(project),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);

    if (repositoryManager.moreThanOneRoot() && getSyncSetting() == GitBranchSyncSetting.NOT_DECIDED) {
      if (!myMultiRootBranchConfig.diverged()) {
        notifyAboutSyncedBranches();
        GitVcsSettings.getInstance(project).setSyncSetting(GitBranchSyncSetting.SYNC);
      }
      else {
        GitVcsSettings.getInstance(project).setSyncSetting(GitBranchSyncSetting.DONT);
      }
    }
  }

  private void notifyAboutSyncedBranches() {
    GitVcs.IMPORTANT_ERROR_NOTIFICATION.createNotification("Synchronous branch control enabled",
      "You have several Git roots in the project and they all are checked out at the same branch. " +
      "We've enabled synchronous branch control for the project. <br/>" +
      "If you wish to control branches in different roots separately, you may <a href='settings'>disable</a> the setting.",
      NotificationType.INFORMATION, new NotificationListener() {
      @Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, GitVcs.getInstance(myProject).getConfigurable().getDisplayName());
          if (getSyncSetting() == GitBranchSyncSetting.DONT) {
            notification.expire();
          }
        }
      }
    }).notify(myProject);
  }

  @NotNull
  private GitBranchSyncSetting getSyncSetting() {
    return GitVcsSettings.getInstance(myProject).getSyncSetting();
  }

  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(myProject);
    if (repositoryManager.moreThanOneRoot()) {

      if (!myMultiRootBranchConfig.diverged() && userWantsSyncControl()) {
        fillWithCommonRepositoryActions(popupGroup, repositoryManager);
      }
      else {
        if (myMultiRootBranchConfig.diverged() && userWantsSyncControl()) {
          warnThatBranchesDiverged(popupGroup);
        }

        fillPopupWithCurrentRepositoryActions(popupGroup, createRepositoriesActions());
      }
    } 
    else {
      fillPopupWithCurrentRepositoryActions(popupGroup, null);
    }

    popupGroup.addSeparator();
    //popupGroup.addAction(new ConfigureAction());
    return popupGroup;
  }

  private boolean userWantsSyncControl() {
    return (getSyncSetting() != GitBranchSyncSetting.DONT);
  }

  private void fillWithCommonRepositoryActions(DefaultActionGroup popupGroup, GitRepositoryManager repositoryManager) {
    Collection<GitRepository> repositories = repositoryManager.getRepositories();
    String currentBranch = myMultiRootBranchConfig.getCurrentBranch();
    assert currentBranch != null : "Current branch can't be null if branches have not diverged";
    popupGroup.add(new GitBranchPopupActions.CurrentBranchAction(currentBranch, " in all roots"));
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

  private void warnThatBranchesDiverged(@NotNull DefaultActionGroup popupGroup) {
    popupGroup.add(new BranchesHaveDivergedMessage(myCurrentRepository));
    popupGroup.addSeparator();
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
  }

  private static class BranchesHaveDivergedMessage extends DumbAwareAction {

    BranchesHaveDivergedMessage(GitRepository currentRepository) {
      super("Branches have diverged, showing current root " + GitUIUtil.getShortRepositoryName(currentRepository), "", IconLoader.getIcon("/general/ideFatalError.png"));
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }

    @Override public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(false);         // this action works as a label
    }
  }

  /**
   * "Configure" opens a dialog to configure branches in the repository, i.e. set up tracked branches, fetch/push branches, etc.
   */
  private static class ConfigureAction extends DumbAwareAction {
    public ConfigureAction() {
      super("Configure", null, IconLoader.getIcon("/general/ideOptions.png")); // TODO description
    }

    @Override public void actionPerformed(AnActionEvent e) {
    }

    @Override
    public void update(AnActionEvent e) {
      //e.getPresentation().setVisible(false);
    }
  }

}
