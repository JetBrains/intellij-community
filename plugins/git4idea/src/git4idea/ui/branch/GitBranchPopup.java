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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  ListPopup asListPopup() {
    return myPopup;
  }

  /**
   *
   * @param project
   * @param currentRepository Current repository, which means the repository of the currently open file. In the case of synchronized branch
   *                   operations current repository doesn't matter.
   * @return
   */
  static GitBranchPopup getInstance(@NotNull Project project, GitRepository currentRepository) {
    return new GitBranchPopup(project, currentRepository);
  }

  private GitBranchPopup(@NotNull Project project, GitRepository currentRepository) {
    myProject = project;
    myCurrentRepository = currentRepository;

    String rootPostFix = GitRepositoryManager.getInstance(project).moreThanOneRoot() ? " on [" + currentRepository.getRoot().getName() + "]" : "";
    String title = "Git Branches" + rootPostFix;

    myPopup = JBPopupFactory.getInstance().createActionGroupPopup(
      title, createActions(),
      SimpleDataContext.getProjectContext(project),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true);
  }

  private GitBranchSyncSetting getSyncSetting() {
    return GitBranchSyncSetting.SYNC;
  }

  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(myProject);
    if (repositoryManager.moreThanOneRoot() && (getSyncSetting() != GitBranchSyncSetting.DONT)) {
      Collection<GitRepository> repositories = repositoryManager.getRepositories();
      GitMultiRootBranchConfig branchConfig = new GitMultiRootBranchConfig(repositories);
      
      if (!branchConfig.diverged()) {
        String currentBranch = branchConfig.getCurrentBranch();
        assert currentBranch != null : "Current branch can't be null if branches have not diverged";
        popupGroup.add(new GitBranchPopupActions.CurrentBranchAction(currentBranch, " in all roots"));
        popupGroup.add(new GitBranchPopupActions.NewBranchAction(myProject, repositories));

        popupGroup.addSeparator("Repositories");
        for (GitRepository repository : repositoryManager.getRepositories()) {
          popupGroup.add(new RootAction(repository, myCurrentRepository));
        }

        popupGroup.addSeparator("Common Local Branches");
        for (String branch : branchConfig.getLocalBranches()) {
          if (!branch.equals(currentBranch)) {
            popupGroup.add(new GitBranchPopupActions.LocalBranchActions(myProject, repositories, branch));
          }
        }

        popupGroup.addSeparator("Common Remote Branches");
        for (String branch : branchConfig.getRemoteBranches()) {
          popupGroup.add(new GitBranchPopupActions.RemoteBranchActions(myProject, repositories, branch));
        }
      }
      else {
        popupGroup.add(new BranchesHaveDivergedMessage());
        fillPopupWithCurrentRepositoryActions(popupGroup);
      }
    } 
    else {
      fillPopupWithCurrentRepositoryActions(popupGroup);
    }

    popupGroup.addSeparator();
    //popupGroup.addAction(new ConfigureAction());
    return popupGroup;
  }

  private void fillPopupWithCurrentRepositoryActions(DefaultActionGroup popupGroup) {
    popupGroup.addAll(new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository).createActions());
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
      ActionGroup group = new GitBranchPopupActions(myRepository.getProject(), myRepository).createActions();
      return group.getChildren(e);
    }
  }

  private static class BranchesHaveDivergedMessage extends DumbAwareAction {

    BranchesHaveDivergedMessage() {
      super("Branches have diverged", "", IconLoader.getIcon("/general/ideFatalError.png"));
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
