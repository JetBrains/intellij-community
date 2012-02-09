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

  ActionGroup createActions(@Nullable DefaultActionGroup toInsert) {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);

    popupGroup.addAction(new NewBranchAction(myProject, Collections.singletonList(myRepository), myRepository));
    popupGroup.addAction(new CheckoutRevisionActions(myProject, myRepository));

    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator("Local Branches");
    List<GitBranch> localBranches = new ArrayList<GitBranch>(myRepository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    for (GitBranch localBranch : localBranches) {
      if (!localBranch.equals(myRepository.getCurrentBranch())) { // don't show current branch in the list
        popupGroup.add(new LocalBranchActions(myProject, Collections.singletonList(myRepository), localBranch.getName(), myRepository));
      }
    }

    popupGroup.addSeparator("Remote Branches");
    List<GitBranch> remoteBranches = new ArrayList<GitBranch>(myRepository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    for (GitBranch remoteBranch : remoteBranches) {
      popupGroup.add(new RemoteBranchActions(myProject, Collections.singletonList(myRepository), remoteBranch.getName(), myRepository));
    }
    
    return popupGroup;
  }
  
  static class NewBranchAction extends DumbAwareAction {
    private final Project myProject;
    private final List<GitRepository> myRepositories;
    @NotNull private final GitRepository mySelectedRepository;

    NewBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull GitRepository selectedRepository) {
      super("New Branch", "Create and checkout new branch", IconLoader.getIcon("/general/add.png"));
      myProject = project;
      myRepositories = repositories;
      mySelectedRepository = selectedRepository;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String name = GitBranchUiUtil.getNewBranchNameFromUser(myProject, myRepositories, "Create New Branch");
      if (name != null) {
        new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).checkoutNewBranch(name);
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

    CheckoutRevisionActions(Project project, GitRepository repository) {
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
    private final List<GitRepository> myRepositories;
    private String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;

    LocalBranchActions(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                       @NotNull GitRepository selectedRepository) {
      super("", true);
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      getTemplatePresentation().setText(calcBranchText(), false); // no mnemonics
    }

    @NotNull
    private String calcBranchText() {
      String trackedBranch = new GitMultiRootBranchConfig(myRepositories).getTrackedBranch(myBranchName);
      if (trackedBranch != null) {
        return myBranchName + " -> " + trackedBranch;
      }
      else {
        return myBranchName;
      }
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new CheckoutAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName, mySelectedRepository),
        new CompareAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new MergeAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new DeleteAction(myProject, myRepositories, myBranchName, mySelectedRepository)
      };
    }

    private static class CheckoutAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;
      @NotNull private final GitRepository mySelectedRepository;

      CheckoutAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                     @NotNull GitRepository selectedRepository) {
        super("Checkout");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
        mySelectedRepository = selectedRepository;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).checkout(myBranchName);
      }

    }

    private static class CheckoutAsNewBranch extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;
      @NotNull private final GitRepository mySelectedRepository;

      CheckoutAsNewBranch(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                          @NotNull GitRepository selectedRepository) {
        super("Checkout as new branch");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
        mySelectedRepository = selectedRepository;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        final String name = Messages
          .showInputDialog(myProject, "Enter name of new branch", "Checkout New Branch From " + myBranchName,
                           Messages.getQuestionIcon(), "", GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).checkoutNewBranchStartingFrom(name, myBranchName);
        }
      }

    }

    /**
     * Action to delete a branch.
     */
    private static class DeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;
      private final GitRepository mySelectedRepository;

      DeleteAction(Project project, List<GitRepository> repositories, String branchName, GitRepository selectedRepository) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
        mySelectedRepository = selectedRepository;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).deleteBranch(myBranchName);
      }
    }
  }

  /**
   * Actions available for remote branches
   */
  static class RemoteBranchActions extends ActionGroup {

    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;

    RemoteBranchActions(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                        @NotNull GitRepository selectedRepository) {
      super("", true);
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new CheckoutRemoteBranchAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new CompareAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new MergeAction(myProject, myRepositories, myBranchName, mySelectedRepository),
      };
    }

    private static class CheckoutRemoteBranchAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myRemoteBranchName;
      @NotNull private final GitRepository mySelectedRepository;

      public CheckoutRemoteBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories,
                                        @NotNull String remoteBranchName, @NotNull GitRepository selectedRepository) {
        super("Checkout as new local branch");
        myProject = project;
        myRepositories = repositories;
        myRemoteBranchName = remoteBranchName;
        mySelectedRepository = selectedRepository;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        final String name = Messages.showInputDialog(myProject, "Enter name of new branch", "Checkout Remote Branch", Messages.getQuestionIcon(),
                                               guessBranchName(), GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).checkoutNewBranchStartingFrom(name, myRemoteBranchName);
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
    private final List<GitRepository> myRepositories;
    private final String myBranchName;
    private final GitRepository mySelectedRepository;

    public CompareAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                         @NotNull GitRepository selectedRepository) {
      super("Compare");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).compare(myBranchName);
    }

  }

  private static class MergeAction extends DumbAwareAction {

    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;
    private final GitRepository mySelectedRepository;

    public MergeAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                       @NotNull GitRepository selectedRepository) {
      super("Merge");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      new GitBranchOperationsProcessor(myProject, myRepositories, mySelectedRepository).merge(myBranchName);
    }

  }
}
