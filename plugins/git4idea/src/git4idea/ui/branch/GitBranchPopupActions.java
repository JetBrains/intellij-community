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

import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;
import git4idea.validators.GitNewBranchNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static git4idea.GitStatisticsCollectorKt.reportUsage;

class GitBranchPopupActions {

  private final Project myProject;
  private final GitRepository myRepository;

  GitBranchPopupActions(Project project, GitRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  ActionGroup createActions(@Nullable DefaultActionGroup toInsert) {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    List<GitRepository> repositoryList = Collections.singletonList(myRepository);

    popupGroup.addAction(new GitNewBranchAction(myProject, repositoryList));
    popupGroup.addAction(new CheckoutRevisionActions(myProject, repositoryList));

    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator("Local Branches");
    List<GitBranch> localBranches = new ArrayList<>(myRepository.getBranches().getLocalBranches());
    Collections.sort(localBranches);
    for (GitBranch localBranch : localBranches) {
      if (!localBranch.equals(myRepository.getCurrentBranch())) { // don't show current branch in the list
        popupGroup.add(new LocalBranchActions(myProject, repositoryList, localBranch.getName(), myRepository));
      }
    }

    popupGroup.addSeparator("Remote Branches");
    List<GitBranch> remoteBranches = new ArrayList<>(myRepository.getBranches().getRemoteBranches());
    Collections.sort(remoteBranches);
    for (GitBranch remoteBranch : remoteBranches) {
      popupGroup.add(new RemoteBranchActions(myProject, repositoryList, remoteBranch.getName(), myRepository));
    }
    
    return popupGroup;
  }

  public static class GitNewBranchAction extends NewBranchAction<GitRepository> {

    public GitNewBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories) {
      super(project, repositories);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String name = GitBranchUtil.getNewBranchNameFromUser(myProject, myRepositories, "Create New Branch");
      if (name != null) {
        GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
        brancher.checkoutNewBranch(name, myRepositories);
        reportUsage("git.branch.create.new");
      }
    }
  }

  /**
   * Checkout manually entered tag or revision number.
   */
  public static class CheckoutRevisionActions extends DumbAwareAction {
    private final Project myProject;
    private final List<GitRepository> myRepositories;

    CheckoutRevisionActions(Project project, List<GitRepository> repositories) {
      super("Checkout Tag or Revision...");
      myProject = project;
      myRepositories = repositories;
    }

    @Override public void actionPerformed(AnActionEvent e) {
      // TODO autocomplete branches, tags.
      // on type check ref validity, on OK check ref existence.
      String reference = Messages
        .showInputDialog(myProject, "Enter reference (branch, tag) name or commit hash", "Checkout", Messages.getQuestionIcon());
      if (reference != null) {
        GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
        brancher.checkout(reference, true, myRepositories, null);
        reportUsage("git.branch.checkout.revision");
      }
    }

    @Override
    public void update(AnActionEvent e) {
      boolean isFresh = ContainerUtil.and(myRepositories, new Condition<GitRepository>() {
        @Override
        public boolean value(GitRepository repository) {
          return repository.isFresh();
        }
      });
      if (isFresh) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout is not possible before the first commit");
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
    List<GitRepository> getRepositories() {
      return myRepositories;
    }

    @NotNull
    public String getBranchName() {
      return myBranchName;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[] {
        new CheckoutAction(myProject, myRepositories, myBranchName),
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName),
        new CompareAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new CheckoutWithRebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, true),
        new RenameBranchAction(myProject, myRepositories, myBranchName),
        new DeleteAction(myProject, myRepositories, myBranchName)
      };
    }

    static class CheckoutAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;

      CheckoutAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
        super("Checkout");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
        brancher.checkout(myBranchName, false, myRepositories, null);
        reportUsage("git.branch.checkout.local");
      }
    }

    private static class CheckoutAsNewBranch extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;

      CheckoutAsNewBranch(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
        super("Checkout as New Branch");
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
          GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
          brancher.checkoutNewBranchStartingFrom(name, myBranchName, myRepositories, null);
        }
        reportUsage("git.checkout.as.new.branch");
      }
    }

    private static class RenameBranchAction extends DumbAwareAction {
      @NotNull private final Project myProject;
      @NotNull private final List<GitRepository> myRepositories;
      @NotNull private final String myCurrentBranchName;

      public RenameBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String currentBranchName) {
        super("Rename");
        myProject = project;
        myRepositories = repositories;
        myCurrentBranchName = currentBranchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        String newName = Messages.showInputDialog(myProject, "Enter new name for the branch " + myCurrentBranchName,
                                                  "Rename Branch " + myCurrentBranchName, Messages.getQuestionIcon(),
                                                  myCurrentBranchName, GitNewBranchNameValidator.newInstance(myRepositories));
        if (newName != null) {
          GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
          brancher.renameBranch(myCurrentBranchName, newName, myRepositories);
          reportUsage("git.branch.rename");
        }
      }
    }

    private static class DeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;

      DeleteAction(Project project, List<GitRepository> repositories, String branchName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
        brancher.deleteBranch(myBranchName, myRepositories);
        reportUsage("git.branch.delete.local");
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
        new CheckoutRemoteBranchAction(myProject, myRepositories, myBranchName),
        new CompareAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, false),
        new RemoteDeleteAction(myProject, myRepositories, myBranchName)
      };
    }

    static class CheckoutRemoteBranchAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myRemoteBranchName;

      public CheckoutRemoteBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories,
                                        @NotNull String remoteBranchName) {
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
          GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
          brancher.checkoutNewBranchStartingFrom(name, myRemoteBranchName, myRepositories, null);
          reportUsage("git.branch.checkout.remote");
        }
      }

      private String guessBranchName() {
        // TODO: check if we already have a branch with that name; check if that branch tracks this remote branch. Show different messages
        int slashPosition = myRemoteBranchName.indexOf("/");
        // if no slash is found (for example, in the case of git-svn remote branches), propose the whole name.
        return myRemoteBranchName.substring(slashPosition+1);
      }
    }

    private static class RemoteDeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;

      RemoteDeleteAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
        brancher.deleteRemoteBranch(myBranchName, myRepositories);
        reportUsage("git.branch.delete.remote");
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
      GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
      brancher.compare(myBranchName, myRepositories, mySelectedRepository);
      reportUsage("git.branch.compare");
    }
  }

  private static class MergeAction extends DumbAwareAction {

    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;
    private final boolean myLocalBranch;

    public MergeAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                       boolean localBranch) {
      super("Merge");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      myLocalBranch = localBranch;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
      brancher.merge(myBranchName, deleteOnMerge(), myRepositories);
      reportUsage("git.branch.merge");
    }

    private GitBrancher.DeleteOnMergeOption deleteOnMerge() {
      if (myLocalBranch && !myBranchName.equals("master")) {
        return GitBrancher.DeleteOnMergeOption.PROPOSE;
      }
      return GitBrancher.DeleteOnMergeOption.NOTHING;
    }
  }

  private static class RebaseAction extends DumbAwareAction {
    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;

    public RebaseAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
      super("Rebase onto");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
      brancher.rebase(myRepositories, myBranchName);
      reportUsage("git.branch.rebase");
    }
  }

  private static class CheckoutWithRebaseAction extends DumbAwareAction {
    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;

    public CheckoutWithRebaseAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
      super("Checkout with Rebase", "Checkout the given branch, and rebase it on current branch in one step, " +
                                    "just like `git rebase " + branchName + " HEAD would do.", null);
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GitBrancher brancher = ServiceManager.getService(myProject, GitBrancher.class);
      brancher.rebaseOnCurrent(myRepositories, myBranchName);
      reportUsage("git.branch.checkout.with.rebase");
    }
  }
}
