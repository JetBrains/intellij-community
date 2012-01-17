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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import git4idea.branch.GitBranchOperationsProcessor;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.validators.GitNewBranchNameValidator;
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
      popupGroup.add(new NewBranchMultiAction(myProject, repositories));

      popupGroup.addSeparator("Repositories");
      for (GitRepository repository : repositoryManager.getRepositories()) {
        popupGroup.add(new RootAction(repository));
      }

      popupGroup.addSeparator("Common Local Branches");
      for (String branch : branchConfig.getLocalBranches()) {
        if (!branch.equals(branchConfig.getCurrentBranch())) {
          popupGroup.add(new LocalBranchMultiActions(myProject, repositories, branch));
        }
      }
    } 
    else {
      popupGroup.addAll(new GitBranchPopupActions(myCurrentRepository.getProject(), myCurrentRepository).createActions());
    }

    popupGroup.addSeparator();
    popupGroup.addAction(new ConfigureAction());
    return popupGroup;
  }

  private static class RootAction extends ActionGroup {

    private final GitRepository myRepository;

    public RootAction(GitRepository repository) {
      super(repository.getPresentableUrl(), true);
      myRepository = repository;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      ActionGroup group = new GitBranchPopupActions(myRepository.getProject(), myRepository).createActions();
      return group.getChildren(e);
    }
  }

  private static class NewBranchMultiAction extends DumbAwareAction {
    private final Project myProject;
    private final Collection<GitRepository> myRepositories;

    private NewBranchMultiAction(Project project, Collection<GitRepository> repositories) {
      super("New Branch", "Create and checkout new branch", IconLoader.getIcon("/general/add.png"));
      myProject = project;
      myRepositories = repositories;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String name = GitBranchUiUtil.getNewBranchNameFromUser(myProject, myRepositories, "Create New Branch");
      if (name != null) {
        new GitBranchOperationsProcessor(myProject, myRepositories).checkoutNewBranch(name);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      if (anyRepositoryIsFresh()) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout of a new branch is not possible before the first commit.");
      }
    }

    private boolean anyRepositoryIsFresh() {
      for (GitRepository repository : myRepositories) {
        if (repository.isFresh()) {
          return true;
        }
      }
      return false;
    }
  }
  
  /**
   * Actions available for local branches.
   */
  private static class LocalBranchMultiActions extends ActionGroup {

    private final Project myProject;
    private final Collection<GitRepository> myRepositories;
    private String myBranchName;

    LocalBranchMultiActions(Project project, Collection<GitRepository> repositories, String branchName) {
      super("", true);
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new CheckoutAction(myProject, myRepositories, myBranchName),
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName),
        new DeleteAction(myProject, myRepositories, myBranchName)
      };
    }

    private static class CheckoutAction extends DumbAwareAction {
      private final Project myProject;
      private final Collection<GitRepository> myRepositories;
      private final String myBranchName;

      public CheckoutAction(Project project, Collection<GitRepository> repositories, String branchName) {
        super("Checkout");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        new GitBranchOperationsProcessor(myProject, myRepositories).checkout(myBranchName);
      }

    }

    private static class CheckoutAsNewBranch extends DumbAwareAction {
      private final Project myProject;
      private final Collection<GitRepository> myRepositories;
      private final String myBranchName;

      public CheckoutAsNewBranch(Project project, @NotNull Collection<GitRepository> repositories, @NotNull String branchName) {
        super("Checkout as new branch");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        final String name = Messages
          .showInputDialog(myProject, "Enter name of new branch", "Checkout New Branch From " + myBranchName,
                           Messages.getQuestionIcon(), "", GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          new GitBranchOperationsProcessor(myProject, myRepositories).checkoutNewBranchStartingFrom(name, myBranchName);
        }
      }

    }

    /**
     * Action to delete a branch.
     */
    private static class DeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final Collection<GitRepository> myRepositories;
      private final String myBranchName;

      public DeleteAction(Project project, Collection<GitRepository> repositories, String branchName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        new GitBranchOperationsProcessor(myProject, myRepositories).deleteBranch(myBranchName);
      }
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
