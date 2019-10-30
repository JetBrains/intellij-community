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

import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EmptyIcon;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitProtectedBranchesKt;
import git4idea.GitRemoteBranch;
import git4idea.actions.GitOngoingOperationAction;
import git4idea.branch.*;
import git4idea.push.GitPushSource;
import git4idea.rebase.GitRebaseSpec;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortHash;
import static com.intellij.dvcs.ui.BranchActionGroupPopup.wrapWithMoreActionIfNeeded;
import static com.intellij.dvcs.ui.BranchActionUtil.FAVORITE_BRANCH_COMPARATOR;
import static com.intellij.dvcs.ui.BranchActionUtil.getNumOfTopShownBranches;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.GitReference.BRANCH_NAME_HASHING_STRATEGY;
import static git4idea.GitUtil.HEAD;
import static git4idea.branch.GitBranchType.LOCAL;
import static git4idea.branch.GitBranchType.REMOTE;
import static git4idea.ui.branch.GitBranchActionsUtilKt.*;
import static java.util.Arrays.asList;
import static one.util.streamex.StreamEx.of;

public class GitBranchPopupActions {

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

    GitRebaseSpec rebaseSpec = GitRepositoryManager.getInstance(myProject).getOngoingRebaseSpec();
    if (rebaseSpec != null && isSpecForRepo(rebaseSpec, myRepository)) {
      popupGroup.addAll(getRebaseActions());
    }
    else {
      popupGroup.addAll(createPerRepoRebaseActions(myRepository));
    }

    popupGroup.addAction(new GitNewBranchAction(myProject, repositoryList));
    popupGroup.addAction(new CheckoutRevisionActions(myProject, repositoryList));

    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator("Local Branches" + repoInfo);
    GitLocalBranch currentBranch = myRepository.getCurrentBranch();
    GitBranchesCollection branchesCollection = myRepository.getBranches();

    List<LocalBranchActions> localBranchActions = of(branchesCollection.getLocalBranches())
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
    List<RemoteBranchActions> remoteBranchActions = of(branchesCollection.getRemoteBranches())
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
    return mapNotNull(getRebaseActions(), action -> createRepositoryRebaseAction(action, repository));
  }

  @NotNull
  static List<AnAction> getRebaseActions() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("Git.Ongoing.Rebase.Actions");
    return asList(group.getChildren(null));
  }

  @Nullable
  private static AnAction createRepositoryRebaseAction(@NotNull AnAction rebaseAction, @NotNull GitRepository repository) {
    if (!(rebaseAction instanceof GitOngoingOperationAction)) return null;
    GitOngoingOperationAction ongoingAction = (GitOngoingOperationAction)rebaseAction;
    DumbAwareAction repositoryAction = new DumbAwareAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(ongoingAction.isEnabled(repository));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ongoingAction.performInBackground(repository);
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
      createOrCheckoutNewBranch(myProject, myRepositories, HEAD);
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
  public static class LocalBranchActions extends BranchActionGroup implements PopupElementWithAdditionalInfo {

    protected final Project myProject;
    protected final List<GitRepository> myRepositories;
    protected final String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;
    private final GitBranchManager myGitBranchManager;
    @NotNull private final GitBranchIncomingOutgoingManager myIncomingOutgoingManager;

    public LocalBranchActions(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName,
                       @NotNull GitRepository selectedRepository) {
      myProject = project;
      myRepositories = immutableList(repositories);
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      myGitBranchManager = ServiceManager.getService(project, GitBranchManager.class);
      myIncomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(myProject);
      getTemplatePresentation().setText(calcBranchText(), false); // no mnemonics
      getTemplatePresentation().putClientProperty(JComponent.TOOL_TIP_TEXT_KEY, constructTooltip());
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

    @Nullable
    private String constructTooltip() {
      boolean incoming = hasIncomingCommits();
      boolean outgoing = hasOutgoingCommits();
      if (!incoming && !outgoing) return null;

      StringBuilder stringBuilder = new StringBuilder("There are ");
      String delimiter = "";
      if (incoming) {
        stringBuilder.append("incoming ");
        delimiter = "and ";
      }
      if (outgoing) {
        stringBuilder.append(delimiter).append("outgoing ");
      }
      stringBuilder.append("commits");
      return stringBuilder.toString();
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new CheckoutAction(myProject, myRepositories, myBranchName),
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName),
        new CheckoutWithRebaseAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName),
        new ShowDiffWithBranchAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, true),
        new Separator(),
        new PushBranchAction(myProject, myRepositories, myBranchName, hasOutgoingCommits()),
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
      return myIncomingOutgoingManager.hasIncomingFor(chooseRepo(), myBranchName);
    }

    @Override
    public boolean hasOutgoingCommits() {
      return myIncomingOutgoingManager.hasOutgoingFor(chooseRepo(), myBranchName);
    }

    public static class CheckoutAction extends DumbAwareAction {
      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myBranchName;

      public CheckoutAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super("Checkout");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        checkoutBranch(myProject, myRepositories, myBranchName);
      }

      public static void checkoutBranch(@NotNull Project project,
                                        @NotNull List<? extends GitRepository> repositories,
                                        @NotNull String branchName) {
        GitBrancher brancher = GitBrancher.getInstance(project);
        brancher.checkout(branchName, false, repositories, null);
      }
    }

    private static class PushBranchAction extends DumbAwareAction implements CustomIconProvider {
      private final Project myProject;
      private final List<GitRepository> myRepositories;
      private final String myBranchName;
      private final boolean myHasCommitsToPush;

      PushBranchAction(@NotNull Project project,
                       @NotNull List<GitRepository> repositories,
                       @NotNull String branchName,
                       boolean hasCommitsToPush) {
        super("Push...");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
        myHasCommitsToPush = hasCommitsToPush;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitLocalBranch localBranch = myRepositories.get(0).getBranches().findLocalBranch(myBranchName);
        assert localBranch != null;
        new VcsPushDialog(myProject, myRepositories, myRepositories, null, GitPushSource.create(localBranch)).show();
      }

      @Nullable
      @Override
      public Icon getRightIcon() {
        return myHasCommitsToPush ? DvcsImplIcons.Outgoing : null;
      }
    }

    public static class RenameBranchAction extends DumbAwareAction {
      @NotNull private final Project myProject;
      @NotNull private final List<? extends GitRepository> myRepositories;
      @NotNull private final String myCurrentBranchName;

      RenameBranchAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String currentBranchName) {
        super("Rename...");
        myProject = project;
        myRepositories = repositories;
        myCurrentBranchName = currentBranchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        rename(myProject, myRepositories, myCurrentBranchName);
      }

      public static void rename(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String currentBranchName) {
        GitNewBranchOptions options =
          new GitNewBranchDialog(project, repositories, "Rename Branch " + currentBranchName, currentBranchName, false, false,
                                 false, GitBranchOperationType.RENAME).showAndGetOptions();
        if (options != null) {
          GitBrancher brancher = GitBrancher.getInstance(project);
          brancher.renameBranch(currentBranchName, options.getName(), repositories);
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
      private final List<? extends GitRepository> myRepositories;
      private final String myBranchName;

      DeleteAction(Project project, List<? extends GitRepository> repositories, String branchName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher.getInstance(myProject)
          .deleteBranch(myBranchName, filter(myRepositories, repository -> !myBranchName.equals(repository.getCurrentBranchName())));
      }
    }
  }

  public static class CurrentBranchActions extends LocalBranchActions {
    public CurrentBranchActions(@NotNull Project project,
                         @NotNull List<? extends GitRepository> repositories,
                         @NotNull String branchName,
                         @NotNull GitRepository selectedRepository) {
      super(project, repositories, branchName, selectedRepository);
      setIcons(DvcsImplIcons.CurrentBranchFavoriteLabel, DvcsImplIcons.CurrentBranchLabel, AllIcons.Nodes.Favorite,
               AllIcons.Nodes.NotFavoriteOnHover);
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{new LocalBranchActions.RenameBranchAction(myProject, myRepositories, myBranchName),
        new LocalBranchActions.PushBranchAction(myProject, myRepositories, myBranchName, hasOutgoingCommits())
      };
    }
  }

  /**
   * Actions available for remote branches
   */
  public static class RemoteBranchActions extends BranchActionGroup {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;
    @NotNull private final GitBranchManager myGitBranchManager;

    public RemoteBranchActions(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName,
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
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName),
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName),
        new ShowDiffWithBranchAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, false),
        new Separator(),
        new RemoteDeleteAction(myProject, myRepositories, myBranchName)
      };
    }

    public static class CheckoutRemoteBranchAction extends DumbAwareAction {
      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myRemoteBranchName;

      CheckoutRemoteBranchAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                 @NotNull String remoteBranchName) {
        super("Checkout");
        myProject = project;
        myRepositories = repositories;
        myRemoteBranchName = remoteBranchName;
      }

      public static void checkoutRemoteBranch(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                              @NotNull String remoteBranchName) {
        GitRepository repository = repositories.get(0);
        GitRemoteBranch remoteBranch = Objects.requireNonNull(repository.getBranches().findRemoteBranch(remoteBranchName));
        String suggestedLocalName = remoteBranch.getNameForRemoteOperations();

        // can have remote conflict if git-svn is used  - suggested local name will be equal to selected remote
        if (BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, suggestedLocalName)) {
          askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName);
          return;
        }

        Map<GitRepository, GitLocalBranch> conflictingLocalBranches = map2MapNotNull(repositories, r -> {
          GitLocalBranch local = r.getBranches().findLocalBranch(suggestedLocalName);
          return local != null ? Pair.create(r, local) : null;
        });

        if (hasTrackingConflicts(conflictingLocalBranches, remoteBranchName)) {
          askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName);
          return;
        }
        boolean hasCommits = !conflictingLocalBranches.isEmpty() &&
                             checkCommitsUnderProgress(project, repositories, remoteBranchName, suggestedLocalName);
        if (hasCommits) {
          int result =
            Messages.showYesNoCancelDialog(
              "Branch " + suggestedLocalName + " already exists and has commits which do not exist in " + remoteBranchName
              + ". Would you like to rebase or reset them?", "Checkout " + remoteBranchName, "Checkout and Rebase", "Overwrite",
              "Cancel", null);
          if (result == Messages.CANCEL) return;
          if (result == Messages.YES) {
            checkout(project, repositories, remoteBranchName, suggestedLocalName, true);
            return;
          }
        }
        GitBrancher brancher = GitBrancher.getInstance(project);
        brancher
          .checkoutNewBranchStartingFrom(suggestedLocalName, remoteBranchName, !conflictingLocalBranches.isEmpty(), repositories, null);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        checkoutRemoteBranch(myProject, myRepositories, myRemoteBranchName);
      }

      private static void askNewBranchNameAndCheckout(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                                      @NotNull String remoteBranchName, @NotNull String suggestedLocalName) {
        //do not allow name conflicts
        GitNewBranchOptions options =
          new GitNewBranchDialog(project, repositories, "Checkout " + remoteBranchName, suggestedLocalName, false, true)
            .showAndGetOptions();
        if (options == null) return;
        GitBrancher brancher = GitBrancher.getInstance(project);
        brancher.checkoutNewBranchStartingFrom(options.getName(), remoteBranchName, options.shouldReset(), repositories, null);
      }

      private static boolean hasTrackingConflicts(@NotNull Map<GitRepository, GitLocalBranch> conflictingLocalBranches, @NotNull String remoteBranchName) {
        return of(conflictingLocalBranches.keySet()).anyMatch(r -> {
          GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(r, conflictingLocalBranches.get(r));
          return trackInfo != null && !BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, trackInfo.getRemoteBranch().getName());
        });
      }
    }

    private static class RemoteDeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myBranchName;

      RemoteDeleteAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.deleteRemoteBranch(myBranchName, myRepositories);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!GitProtectedBranchesKt.isRemoteBranchProtected(myRepositories, myBranchName));
      }
    }
  }

  private static class CheckoutAsNewBranch extends DumbAwareAction {
    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    CheckoutAsNewBranch(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
      super("New Branch from Selected...");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitNewBranchOptions newBranchOptions =
        new GitNewBranchDialog(myProject, myRepositories, "Checkout New Branch From " + myBranchName, "", false, true, true)
          .showAndGetOptions();
      if (newBranchOptions != null) {
        checkoutOrReset(myProject, myRepositories, myBranchName + "^0", newBranchOptions);
      }
    }
  }

  private static class CompareAction extends DumbAwareAction {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    CompareAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
      super("Compare with Current");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      FileDocumentManager.getInstance().saveAllDocuments();

      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.compare(myBranchName, myRepositories);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      String description = String.format("Show commits in %1$s that are missing in %2$s",
                                         getBranchPresentation(myBranchName),
                                         getCurrentBranchPresentation(myRepositories));
      e.getPresentation().setDescription(description);
    }
  }

  private static class ShowDiffWithBranchAction extends DumbAwareAction {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    ShowDiffWithBranchAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
      super("Show Diff with Working Tree");
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher.getInstance(myProject)
        .showDiffWithLocal(myBranchName, filter(myRepositories, repository -> !myBranchName.equals(repository.getCurrentBranchName())));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(!new GitMultiRootBranchConfig(myRepositories).diverged());
      String description = String.format("Compare the current working tree with the tree in %1$s", getBranchPresentation(myBranchName));
      e.getPresentation().setDescription(description);
    }
  }

  private static class MergeAction extends DumbAwareAction {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;
    private final boolean myLocalBranch;

    MergeAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName,
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
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    RebaseAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
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
    }
  }

  private static class CheckoutWithRebaseAction extends DumbAwareAction {
    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    CheckoutWithRebaseAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
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
    }
  }

  static class TagActions extends BranchActionGroup {
    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myTagName;

    TagActions(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String tagName) {
      myProject = project;
      myRepositories = repositories;
      myTagName = tagName;
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
      private final List<? extends GitRepository> myRepositories;
      private final String myTagName;

      DeleteTagAction(Project project, List<? extends GitRepository> repositories, String tagName) {
        super("Delete");
        myProject = project;
        myRepositories = repositories;
        myTagName = tagName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.deleteTag(myTagName, myRepositories);
      }
    }
  }

  @NotNull
  private static String getCurrentBranchPresentation(@NotNull Collection<? extends GitRepository> repositories) {
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
