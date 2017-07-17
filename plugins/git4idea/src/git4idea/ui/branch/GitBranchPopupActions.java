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

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.dvcs.ui.PopupElementWithAdditionalInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBrancher;
import git4idea.branch.GitBranchesCollection;
import git4idea.branch.GitNewBranchOptions;
import git4idea.repo.GitRepository;
import git4idea.validators.GitNewBranchNameValidator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static git4idea.GitStatisticsCollectorKt.reportUsage;
import static git4idea.GitUtil.HEAD;
import static git4idea.branch.GitBranchType.LOCAL;
import static git4idea.branch.GitBranchType.REMOTE;

class GitBranchPopupActions {

  private final Project myProject;
  private final GitRepository myRepository;

  GitBranchPopupActions(Project project, GitRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  ActionGroup createActions() {
    return createActions(null, "", false);
  }

  ActionGroup createActions(@Nullable DefaultActionGroup toInsert, @NotNull String repoInfo, boolean firstLevelGroup) {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    List<GitRepository> repositoryList = Collections.singletonList(myRepository);

    popupGroup.addAction(new GitNewBranchAction(myProject, repositoryList));
    popupGroup.addAction(new CheckoutRevisionActions(myProject, repositoryList));

    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator("Local Branches" + repoInfo);
    GitLocalBranch currentBranch = myRepository.getCurrentBranch();
    GitBranchesCollection branchesCollection = myRepository.getBranches();

    List<LocalBranchActions> localBranchActions = StreamEx.of(branchesCollection.getLocalBranches())
      .filter(branch -> !branch.equals(currentBranch))
      .map(GitBranch::getName)
      .sorted(StringUtil::naturalCompare)
      .map(localName -> new LocalBranchActions(myProject, repositoryList, localName, myRepository))
      .sorted(FAVORITE_BRANCH_COMPARATOR)
      .toList();
    int topShownBranches = getNumOfTopShownBranches(localBranchActions);
    if (currentBranch != null) {
      localBranchActions.add(0, new CurrentBranchActions(myProject, repositoryList, currentBranch.getName(), myRepository));
      topShownBranches++;
    }
    // if there are only a few local favorites -> show all;  for remotes it's better to show only favorites; 
    wrapWithMoreActionIfNeeded(myProject, popupGroup, localBranchActions,
                               topShownBranches, firstLevelGroup ? GitBranchPopup.SHOW_ALL_LOCALS_KEY : null,
                               firstLevelGroup);

    popupGroup.addSeparator("Remote Branches" + repoInfo);
    List<RemoteBranchActions> remoteBranchActions = StreamEx.of(branchesCollection.getRemoteBranches())
      .map(GitBranch::getName)
      .sorted(StringUtil::naturalCompare)
      .map(remoteName -> new RemoteBranchActions(myProject, repositoryList, remoteName, myRepository))
      .toList();
    wrapWithMoreActionIfNeeded(myProject, popupGroup, ContainerUtil.sorted(remoteBranchActions, FAVORITE_BRANCH_COMPARATOR),
                               getNumOfTopShownBranches(remoteBranchActions), firstLevelGroup ? GitBranchPopup.SHOW_ALL_REMOTES_KEY : null);
    return popupGroup;
  }

  public static class GitNewBranchAction extends NewBranchAction<GitRepository> {

    public GitNewBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories) {
      super(project, repositories);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GitNewBranchOptions options = GitBranchUtil.getNewBranchNameFromUser(myProject, myRepositories, "Create New Branch", null);
      if (options != null) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        if (options.shouldCheckout()) {
          reportUsage("git.branch.create.new");
          brancher.checkoutNewBranch(options.getName(), myRepositories);
        }
        else {
          reportUsage("git.branch.create.new.nocheckout");
          brancher.createBranch(options.getName(), StreamEx.of(myRepositories).toMap(position -> HEAD));
        }
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

    @Override
    public void actionPerformed(AnActionEvent e) {
      // TODO autocomplete branches, tags.
      // on type check ref validity, on OK check ref existence.
      String reference = Messages
        .showInputDialog(myProject, "Enter reference (branch, tag) name or commit hash:", "Checkout", null);
      if (reference != null) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.checkout(reference, true, myRepositories, null);
        reportUsage("git.branch.checkout.revision");
      }
    }

    @Override
    public void update(AnActionEvent e) {
      boolean isFresh = ContainerUtil.and(myRepositories, repository -> repository.isFresh());
      if (isFresh) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout is not possible before the first commit");
      }
    }
  }

  /**
   * Actions available for local branches.
   */
  static class LocalBranchActions extends BranchActionGroup implements PopupElementWithAdditionalInfo {

    protected final Project myProject;
    protected final List<GitRepository> myRepositories;
    protected final String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;
    private final GitBranchManager myGitBranchManager;

    LocalBranchActions(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                       @NotNull GitRepository selectedRepository) {
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      myGitBranchManager = ServiceManager.getService(project, GitBranchManager.class);
      getTemplatePresentation().setText(calcBranchText(), false); // no mnemonics
      setFavorite(myGitBranchManager.isFavorite(LOCAL, repositories.size() > 1 ? null : mySelectedRepository, myBranchName));
    }

    @NotNull
    private String calcBranchText() {
      return myBranchName;
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
      return new AnAction[]{
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

    @Override
    @Nullable
    public String getInfoText() {
      return new GitMultiRootBranchConfig(myRepositories).getTrackedBranch(myBranchName);
    }

    @Override
    public void toggle() {
      super.toggle();
      myGitBranchManager.setFavorite(LOCAL, myRepositories.size() > 1 ? null : mySelectedRepository, myBranchName, isFavorite());
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
        GitBrancher brancher = GitBrancher.getInstance(myProject);
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
          .showInputDialog(myProject, "New branch name:", "Checkout New Branch From " + myBranchName,
                           null, "", GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          GitBrancher brancher = GitBrancher.getInstance(myProject);
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
        String newName = Messages.showInputDialog(myProject, "New name for the branch '" + myCurrentBranchName + "':",
                                                  "Rename Branch " + myCurrentBranchName, null,
                                                  myCurrentBranchName, GitNewBranchNameValidator.newInstance(myRepositories));
        if (newName != null) {
          GitBrancher brancher = GitBrancher.getInstance(myProject);
          brancher.renameBranch(myCurrentBranchName, newName, myRepositories);
          reportUsage("git.branch.rename");
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        if (myRepositories.stream().anyMatch(Repository::isFresh)) {
          e.getPresentation().setEnabled(false);
          e.getPresentation().setDescription("Renaming branch is not possible before the first commit");
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
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.deleteBranch(myBranchName, myRepositories);
        reportUsage("git.branch.delete.local");
      }
    }
  }

  static class CurrentBranchActions extends LocalBranchActions {
    CurrentBranchActions(@NotNull Project project,
                         @NotNull List<GitRepository> repositories,
                         @NotNull String branchName,
                         @NotNull GitRepository selectedRepository) {
      super(project, repositories, branchName, selectedRepository);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{new LocalBranchActions.RenameBranchAction(myProject, myRepositories, myBranchName)};
    }

    @Nullable
    @Override
    public String getPrefixInfo() {
      return "current";
    }
  }

  /**
   * Actions available for remote branches
   */
  static class RemoteBranchActions extends BranchActionGroup {

    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;
    @NotNull private final GitBranchManager myGitBranchManager;

    RemoteBranchActions(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                        @NotNull GitRepository selectedRepository) {

      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      myGitBranchManager = ServiceManager.getService(project, GitBranchManager.class);
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics
      setFavorite(myGitBranchManager.isFavorite(REMOTE, repositories.size() > 1 ? null : mySelectedRepository, myBranchName));
    }

    @Override
    public void toggle() {
      super.toggle();
      myGitBranchManager.setFavorite(REMOTE, myRepositories.size() > 1 ? null : mySelectedRepository, myBranchName, isFavorite());
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
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
        final String name = Messages.showInputDialog(myProject, "New branch name:", "Checkout Remote Branch", null,
                                                     guessBranchName(), GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          GitBrancher brancher = GitBrancher.getInstance(myProject);
          brancher.checkoutNewBranchStartingFrom(name, myRemoteBranchName, myRepositories, null);
          reportUsage("git.branch.checkout.remote");
        }
      }

      private String guessBranchName() {
        // TODO: check if we already have a branch with that name; check if that branch tracks this remote branch. Show different messages
        int slashPosition = myRemoteBranchName.indexOf("/");
        // if no slash is found (for example, in the case of git-svn remote branches), propose the whole name.
        return myRemoteBranchName.substring(slashPosition + 1);
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
        GitBrancher brancher = GitBrancher.getInstance(myProject);
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
      GitBrancher brancher = GitBrancher.getInstance(myProject);
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
      GitBrancher brancher = GitBrancher.getInstance(myProject);
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
      GitBrancher brancher = GitBrancher.getInstance(myProject);
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
                                    "just like `git rebase HEAD " + branchName + "` would do.", null);
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.rebaseOnCurrent(myRepositories, myBranchName);
      reportUsage("git.branch.checkout.with.rebase");
    }
  }
}
