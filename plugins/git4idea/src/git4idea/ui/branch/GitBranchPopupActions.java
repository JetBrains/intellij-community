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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import git4idea.GitBranch;
import git4idea.branch.GitBranchOperationsProcessor;
import git4idea.repo.GitRepository;
import git4idea.validators.GitNewBranchNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Kirill Likhodedov
 */
class GitBranchPopupActions {

  private static final Logger LOG = Logger.getInstance(GitBranchPopupActions.class);
  private final Project myProject;
  private final GitRepository myRepository;

  GitBranchPopupActions(Project project, GitRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    popupGroup.addAction(new CurrentBranchAction(myRepository));
    popupGroup.addAction(new NewBranchAction(myProject, Collections.singleton(myRepository)));
    popupGroup.addAction(new CheckoutRevisionActions(myProject, myRepository));

    popupGroup.addSeparator("Local Branches");
    List<GitBranch> localBranches = new ArrayList<GitBranch>(myRepository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    for (GitBranch localBranch : localBranches) {
      if (!localBranch.equals(myRepository.getCurrentBranch())) { // don't show current branch in the list
        popupGroup.add(new LocalBranchActions(myProject, Collections.singleton(myRepository), localBranch.getName()));
      }
    }

    popupGroup.addSeparator("Remote Branches");
    List<GitBranch> remoteBranches = new ArrayList<GitBranch>(myRepository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    for (GitBranch remoteBranch : remoteBranches) {
      popupGroup.add(new RemoteBranchActions(myProject, Collections.singleton(myRepository), remoteBranch.getName()));
    }
    
    return popupGroup;
  }
  
  /**
   * "Current branch:" item which is disabled and is just a label to display the current branch.
   */
  private static class CurrentBranchAction extends DumbAwareAction {
    public CurrentBranchAction(GitRepository repository) {
      super("", String.format("Current branch [%s] in root [%s]", getBranchText(repository), repository.getRoot().getName()), null);
      getTemplatePresentation().setText("Current Branch: " + getBranchText(repository), false); // no mnemonics
    }

    private static String getBranchText(GitRepository repository) {
      return GitBranchUiUtil.getDisplayableBranchText(repository);
    }

    @Override public void actionPerformed(AnActionEvent e) {
    }

    @Override public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(false);         // this action works as a label
    }
  }

  static class NewBranchAction extends DumbAwareAction {
    private final Project myProject;
    private final Collection<GitRepository> myRepositories;

    NewBranchAction(@NotNull Project project, @NotNull Collection<GitRepository> repositories) {
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
   * Checkout manually entered tag or revision number.
   */
  private static class CheckoutRevisionActions extends DumbAwareAction {
    private final Project myProject;
    private final GitRepository myRepository;

    public CheckoutRevisionActions(Project project, GitRepository repository) {
      super("Checkout Tag or Revision");
      myProject = project;
      myRepository = repository;
    }

    @Override public void actionPerformed(AnActionEvent e) {
      // TODO autocomplete branches, tags.
      // on type check ref validity, on OK check ref existence.
      String reference = Messages
        .showInputDialog(myProject, "Enter reference (branch, tag) name or commit hash", "Checkout", Messages.getQuestionIcon());
      if (reference != null) {
        new GitBranchOperationsProcessor(myRepository).checkout(reference);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      if (myRepository.isFresh()) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout is not possible before the first commit.");
      }
    }
  }

  /**
   * Actions available for local branches.
   */
  static class LocalBranchActions extends ActionGroup {

    private final Project myProject;
    private final Collection<GitRepository> myRepositories;
    private String myBranchName;

    LocalBranchActions(Project project, Collection<GitRepository> repositories, String branchName) {
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
   * Actions available for remote branches
   */
  static class RemoteBranchActions extends ActionGroup {

    private final Project myProject;
    private final Collection<GitRepository> myRepositories;
    private String myBranchName;

    RemoteBranchActions(Project project, Collection<GitRepository> repositories, String branchName) {
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
        new CheckoutRemoteBranchAction(myProject, myRepositories, myBranchName),
        new CompareAction(myProject, myRepositories, myBranchName),
      };
    }

    private static class CheckoutRemoteBranchAction extends DumbAwareAction {
      private final Project myProject;
      private final Collection<GitRepository> myRepositories;
      private final String myRemoteBranchName;

      public CheckoutRemoteBranchAction(Project project, Collection<GitRepository> repositories, String remoteBranchName) {
        super("Checkout as new local branch");
        myProject = project;
        myRepositories = repositories;
        myRemoteBranchName = remoteBranchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        final String name = Messages.showInputDialog(myProject, "Enter name of new branch", "Checkout Remote Branch", Messages.getQuestionIcon(),
                                               guessBranchName(), GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          new GitBranchOperationsProcessor(myProject, myRepositories).checkoutNewBranchStartingFrom(name, myRemoteBranchName);
        }
      }

      private String guessBranchName() {
        // TODO: check if we already have a branch with that name; check if that branch tracks this remote branch. Show different messages
        int slashPosition = myRemoteBranchName.indexOf("/");
        LOG.assertTrue(slashPosition > 0, "Remote branch name should have a slash separator: [" + myRemoteBranchName + "]");
        return myRemoteBranchName.substring(slashPosition+1);
      }
    }

  }
  
  private static class CompareAction extends DumbAwareAction {

    private final Project myProject;
    private final Collection<GitRepository> myRepositories;
    private final String myBranchName;

    public CompareAction(@NotNull Project project, @NotNull Collection<GitRepository> repositories, @NotNull String branchName) {
      super("Compare");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      new GitBranchOperationsProcessor(myProject, myRepositories).compare(myBranchName);
    }

  }
}
