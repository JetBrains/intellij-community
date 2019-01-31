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
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.dvcs.ui.PopupElementWithAdditionalInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EmptyIcon;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitProtectedBranchesKt;
import git4idea.actions.GitAbstractRebaseAction;
import git4idea.branch.*;
import git4idea.config.GitVcsSettings;
import git4idea.rebase.GitRebaseSpec;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.validators.GitNewBranchNameValidator;
import icons.DvcsImplIcons;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.dvcs.DvcsUtil.getShortHash;
import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.GitUsagesTriggerCollector.reportUsage;
import static git4idea.GitUtil.HEAD;
import static git4idea.branch.GitBranchType.LOCAL;
import static git4idea.branch.GitBranchType.REMOTE;
import static java.util.Arrays.asList;

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

  ActionGroup createActions(@Nullable LightActionGroup toInsert, @NotNull String repoInfo, boolean firstLevelGroup) {
    LightActionGroup popupGroup = new LightActionGroup(false);
    List<GitRepository> repositoryList = Collections.singletonList(myRepository);

    if (myRepository.isRebaseInProgress()) {
      GitRebaseSpec rebaseSpec = GitRepositoryManager.getInstance(myProject).getOngoingRebaseSpec();
      popupGroup.addAll(
        rebaseSpec != null && isSpecForRepo(rebaseSpec, myRepository) ? getRebaseActions() : createPerRepoRebaseActions(myRepository));
    }

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
      .map(branch -> new LocalBranchActions(myProject, repositoryList, branch.getName(), myRepository))
      .sorted((b1, b2) -> {
        int delta = FAVORITE_BRANCH_COMPARATOR.compare(b1, b2);
        if (delta != 0) return delta;
        return StringUtil.naturalCompare(b1.myBranchName, b2.myBranchName);
      })
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
    wrapWithMoreActionIfNeeded(myProject, popupGroup, sorted(remoteBranchActions, FAVORITE_BRANCH_COMPARATOR),
                               getNumOfTopShownBranches(remoteBranchActions), firstLevelGroup ? GitBranchPopup.SHOW_ALL_REMOTES_KEY : null);
    return popupGroup;
  }

  private static boolean isSpecForRepo(@NotNull GitRebaseSpec spec, @NotNull GitRepository repository) {
    Collection<GitRepository> repositoriesFromSpec = spec.getAllRepositories();
    return repositoriesFromSpec.size() == 1 && repository.equals(getFirstItem(repositoriesFromSpec));
  }

  @NotNull
  private static List<AnAction> createPerRepoRebaseActions(@NotNull GitRepository repository) {
    return asList(createRepositoryRebaseAction("Git.Rebase.Abort", repository),
                  createRepositoryRebaseAction("Git.Rebase.Continue", repository),
                  createRepositoryRebaseAction("Git.Rebase.Skip", repository));
  }

  @NotNull
  static List<AnAction> getRebaseActions() {
    ActionManager actionManager = ActionManager.getInstance();
    return asList(actionManager.getAction("Git.Rebase.Abort"),
                  actionManager.getAction("Git.Rebase.Continue"),
                  actionManager.getAction("Git.Rebase.Skip"));
  }

  @NotNull
  private static AnAction createRepositoryRebaseAction(@NotNull String rebaseActionId, @NotNull GitRepository repository) {
    GitAbstractRebaseAction rebaseAction = notNull((GitAbstractRebaseAction)ActionManager.getInstance().getAction(rebaseActionId));
    DumbAwareAction repositoryAction = new DumbAwareAction() {

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(repository.isRebaseInProgress());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        rebaseAction.performInBackground(repository);
      }
    };
    repositoryAction.getTemplatePresentation().copyFrom(rebaseAction.getTemplatePresentation());
    return repositoryAction;
  }

  public static class GitNewBranchAction extends NewBranchAction<GitRepository> {

    public GitNewBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories) {
      super(project, repositories);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitNewBranchOptions options = GitBranchUtil.getNewBranchNameFromUser(myProject, myRepositories, "Create New Branch", null);
      if (options != null) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        if (options.shouldCheckout()) {
          reportUsage(myProject, "git.branch.create.new");
          brancher.checkoutNewBranch(options.getName(), myRepositories);
        }
        else {
          reportUsage(myProject, "git.branch.create.new.nocheckout");
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
    public void actionPerformed(@NotNull AnActionEvent e) {
      // TODO: on type check ref validity, on OK check ref existence.

      GitRefDialog dialog = new GitRefDialog(myProject, myRepositories, "Checkout",
                                             "Enter reference (branch, tag) name or commit hash:");
      if (dialog.showAndGet()) {
        String reference = dialog.getReference();
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.checkout(reference, true, myRepositories, null);
        reportUsage(myProject, "git.branch.checkout.revision");
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean isFresh = and(myRepositories, repository -> repository.isFresh());
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
    @NotNull private final GitVcsSettings myGitVcsSettings;
    @NotNull private final GitBranchIncomingOutgoingManager myIncomingOutgoingManager;

    LocalBranchActions(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                       @NotNull GitRepository selectedRepository) {
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      myGitBranchManager = ServiceManager.getService(project, GitBranchManager.class);
      myGitVcsSettings = GitVcsSettings.getInstance(myProject);
      myIncomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(myProject);
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
        new CheckoutWithRebaseAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, true),
        new Separator(),
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
      myGitBranchManager.setFavorite(LOCAL, chooseRepo(), myBranchName, isFavorite());
    }

    @Nullable
    private GitRepository chooseRepo() {
      return myRepositories.size() > 1 ? null : mySelectedRepository;
    }

    @Override
    public boolean hasIncomingCommits() {
      return myGitVcsSettings.shouldUpdateBranchInfo() &&
             myIncomingOutgoingManager.hasIncomingFor(chooseRepo(), myBranchName);
    }

    @Override
    public boolean hasOutgoingCommits() {
      return myGitVcsSettings.shouldUpdateBranchInfo() &&
             myIncomingOutgoingManager.hasOutgoingFor(chooseRepo(), myBranchName);
    }

    private static class CheckoutAction extends DumbAwareAction {
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
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.checkout(myBranchName, false, myRepositories, null);
        reportUsage(myProject, "git.branch.checkout.local");
      }
    }

    private static class CheckoutAsNewBranch extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;

      CheckoutAsNewBranch(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
        super("Checkout As...");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final String name = Messages
          .showInputDialog(myProject, "New branch name:", "Checkout New Branch From " + myBranchName,
                           null, "", GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          GitBrancher brancher = GitBrancher.getInstance(myProject);
          brancher.checkoutNewBranchStartingFrom(name, myBranchName, myRepositories, null);
        }
        reportUsage(myProject, "git.checkout.as.new.branch");
      }
    }

    private static class RenameBranchAction extends DumbAwareAction {
      @NotNull private final Project myProject;
      @NotNull private final List<GitRepository> myRepositories;
      @NotNull private final String myCurrentBranchName;

      RenameBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String currentBranchName) {
        super("Rename...");
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
          reportUsage(myProject, "git.branch.rename");
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
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.deleteBranch(myBranchName, myRepositories);
        reportUsage(myProject, "git.branch.delete.local");
      }
    }
  }

  static class CurrentBranchActions extends LocalBranchActions {
    CurrentBranchActions(@NotNull Project project,
                         @NotNull List<GitRepository> repositories,
                         @NotNull String branchName,
                         @NotNull GitRepository selectedRepository) {
      super(project, repositories, branchName, selectedRepository);
      setIcons(DvcsImplIcons.CurrentBranchFavoriteLabel, DvcsImplIcons.CurrentBranchLabel, AllIcons.Nodes.Favorite,
               AllIcons.Nodes.NotFavoriteOnHover);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{new LocalBranchActions.RenameBranchAction(myProject, myRepositories, myBranchName)};
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
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName, mySelectedRepository),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, false),
        new Separator(),
        new RemoteDeleteAction(myProject, myRepositories, myBranchName)
      };
    }

    private static class CheckoutRemoteBranchAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myRemoteBranchName;

      CheckoutRemoteBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories,
                                        @NotNull String remoteBranchName) {
        super("Checkout As...");
        myProject = project;
        myRepositories = repositories;
        myRemoteBranchName = remoteBranchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final String name = Messages.showInputDialog(myProject, "New branch name:", "Checkout Remote Branch", null,
                                                     guessBranchName(), GitNewBranchNameValidator.newInstance(myRepositories));
        if (name != null) {
          GitBrancher brancher = GitBrancher.getInstance(myProject);
          brancher.checkoutNewBranchStartingFrom(name, myRemoteBranchName, myRepositories, null);
          reportUsage(myProject, "git.branch.checkout.remote");
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
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.deleteRemoteBranch(myBranchName, myRepositories);
        reportUsage(myProject, "git.branch.delete.remote");
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!GitProtectedBranchesKt.isRemoteBranchProtected(myRepositories, myBranchName));
      }
    }
  }

  private static class CompareAction extends DumbAwareAction {

    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;
    private final GitRepository mySelectedRepository;

    CompareAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                         @NotNull GitRepository selectedRepository) {
      super("Compare with Current");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();

      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.compare(myBranchName, myRepositories, mySelectedRepository);
      reportUsage(myProject, "git.branch.compare");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      String description = String.format("Compare commits in %1$s and %2$s, and the file tree in %1$s and its current state",
                                         getBranchPresentation(myBranchName),
                                         getCurrentBranchPresentation(myRepositories));
      e.getPresentation().setDescription(description);
    }
  }

  private static class MergeAction extends DumbAwareAction {

    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;
    private final boolean myLocalBranch;

    MergeAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName,
                       boolean localBranch) {
      super("Merge into Current");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      myLocalBranch = localBranch;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      String description = String.format("Merge %s into %s",
                                         getBranchPresentation(myBranchName),
                                         getCurrentBranchPresentation(myRepositories));
      e.getPresentation().setDescription(description);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.merge(myBranchName, deleteOnMerge(), myRepositories);
      reportUsage(myProject, "git.branch.merge");
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

    RebaseAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
      super("Rebase Current onto Selected");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean isOnBranch = and(myRepositories, GitRepository::isOnBranch);

      String description = isOnBranch
                           ? String.format("Rebase %s onto %s",
                                           getCurrentBranchPresentation(myRepositories),
                                           getBranchPresentation(myBranchName))
                           : "Rebase is not possible in the detached HEAD state";
      e.getPresentation().setDescription(description);
      e.getPresentation().setEnabled(isOnBranch);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.rebase(myRepositories, myBranchName);
      reportUsage(myProject, "git.branch.rebase");
    }
  }

  private static class CheckoutWithRebaseAction extends DumbAwareAction {
    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myBranchName;

    CheckoutWithRebaseAction(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String branchName) {
      super("Checkout and Rebase onto Current");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      String description = String.format("Checkout %s, and rebase it onto %s in one step (like `git rebase HEAD %s`)",
                                         getBranchPresentation(myBranchName),
                                         getCurrentBranchPresentation(myRepositories),
                                         myBranchName);
      e.getPresentation().setDescription(description);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.rebaseOnCurrent(myRepositories, myBranchName);
      reportUsage(myProject, "git.branch.checkout.with.rebase");
    }
  }

  static class TagActions extends BranchActionGroup {
    private final Project myProject;
    private final List<GitRepository> myRepositories;
    private final String myTagName;
    private final GitRepository mySelectedRepository;

    TagActions(@NotNull Project project, @NotNull List<GitRepository> repositories, @NotNull String tagName,
               @NotNull GitRepository selectedRepository) {
      myProject = project;
      myRepositories = repositories;
      myTagName = tagName;
      mySelectedRepository = selectedRepository;
      getTemplatePresentation().setText(tagName, false); // no mnemonics
      setIcons(EmptyIcon.ICON_16, EmptyIcon.ICON_16, EmptyIcon.ICON_16, EmptyIcon.ICON_16); // no favorites
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new DeleteTagAction(myProject, myRepositories, myTagName)
      };
    }

    private static class DeleteTagAction extends DumbAwareAction {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myTagName;

      DeleteTagAction(Project project, List<GitRepository> repositories, String tagName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myTagName = tagName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.deleteTag(myTagName, myRepositories);
        reportUsage(myProject, "git.tag.delete.local");
      }
    }
  }

  @NotNull
  private static String getCurrentBranchPresentation(@NotNull Collection<GitRepository> repositories) {
    Set<String> currentBranches = map2Set(repositories,
                                          repo -> notNull(repo.getCurrentBranchName(),
                                                          getShortHash(notNull(repo.getCurrentRevision()))));
    if (currentBranches.size() == 1) return getBranchPresentation(currentBranches.iterator().next());
    return "current branch";
  }

  @NotNull
  private static String getBranchPresentation(@NotNull String branch) {
    return "'" + branch + "'";
  }
}
