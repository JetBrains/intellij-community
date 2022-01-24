// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.EmptyIcon;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitProtectedBranchesKt;
import git4idea.GitRemoteBranch;
import git4idea.actions.GitOngoingOperationAction;
import git4idea.branch.*;
import git4idea.config.GitVcsSettings;
import git4idea.config.UpdateMethod;
import git4idea.fetch.GitFetchSupport;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushSource;
import git4idea.rebase.GitRebaseSpec;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitUpdateExecutionProcess;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.dvcs.DvcsUtil.disableActionIfAnyRepositoryIsFresh;
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
    return createActions(null, null, false);
  }

  ActionGroup createActions(@Nullable LightActionGroup toInsert, @Nullable GitRepository specificRepository, boolean firstLevelGroup) {
    LightActionGroup popupGroup = new LightActionGroup(false);
    List<GitRepository> repositoryList = Collections.singletonList(myRepository);

    GitRebaseSpec rebaseSpec = GitRepositoryManager.getInstance(myProject).getOngoingRebaseSpec();
    if (rebaseSpec != null && isSpecForRepo(rebaseSpec, myRepository)) {
      popupGroup.addAll(getRebaseActions());
    }
    else {
      popupGroup.addAll(createPerRepoRebaseActions(myRepository));
    }

    if (ExperimentalUI.isNewVcsBranchPopup()) {
      ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction("Git.Experimental.Branch.Popup.Actions");
      popupGroup.addAll(actionGroup);
      popupGroup.addSeparator();
    }

    popupGroup.addAction(new GitNewBranchAction(myProject, repositoryList));

    if (!ExperimentalUI.isNewVcsBranchPopup()) {
      popupGroup.addAction(new CheckoutRevisionActions(myProject, repositoryList));
    }

    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator(specificRepository == null ?
                            GitBundle.message("branches.local.branches") :
                            GitBundle.message("branches.local.branches.in.repo", DvcsUtil.getShortRepositoryName(specificRepository)));
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

    popupGroup.addSeparator(specificRepository == null ?
                            GitBundle.message("branches.remote.branches") :
                            GitBundle.message("branches.remote.branches.in.repo", specificRepository));
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
      super(GitBundle.messagePointer("branches.checkout.tag.or.revision"));
      myProject = project;
      myRepositories = repositories;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // TODO: on type check ref validity, on OK check ref existence.

      GitRefDialog dialog = new GitRefDialog(myProject, myRepositories, GitBundle.message("branches.checkout"),
                                             GitBundle.message("branches.enter.reference.branch.tag.name.or.commit.hash"));
      if (dialog.showAndGet()) {
        String reference = dialog.getReference();
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.checkout(reference, true, myRepositories, null);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      disableActionIfAnyRepositoryIsFresh(e, myRepositories, GitBundle.message("action.not.possible.in.fresh.repo.checkout"));
    }
  }

  /**
   * Actions available for local branches.
   */
  public static class LocalBranchActions extends BranchActionGroup implements PopupElementWithAdditionalInfo {

    protected final Project myProject;
    protected final List<GitRepository> myRepositories;
    protected final @NlsSafe String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;
    private final GitBranchManager myGitBranchManager;
    @NotNull private final GitBranchIncomingOutgoingManager myIncomingOutgoingManager;

    public LocalBranchActions(@NotNull Project project,
                              @NotNull List<? extends GitRepository> repositories,
                              @NotNull @NlsSafe String branchName,
                              @NotNull GitRepository selectedRepository) {
      myProject = project;
      myRepositories = immutableList(repositories);
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      myGitBranchManager = project.getService(GitBranchManager.class);
      myIncomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(myProject);
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics

      addTooltipText(getTemplatePresentation(), constructIncomingOutgoingTooltip(hasIncomingCommits(), hasOutgoingCommits()));
      setFavorite(myGitBranchManager.isFavorite(LOCAL, repositories.size() > 1 ? null : mySelectedRepository, myBranchName));
    }

    @NotNull
    List<GitRepository> getRepositories() {
      return myRepositories;
    }

    @NotNull
    public String getBranchName() {
      return myBranchName;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Nullable
    public static String constructIncomingOutgoingTooltip(boolean incoming, boolean outgoing) {
      if (!incoming && !outgoing) return null;

      if (incoming && outgoing) {
        return GitBundle.message("branches.there.are.incoming.and.outgoing.commits");
      }
      if (incoming) {
        return GitBundle.message("branches.there.are.incoming.commits");
      }
      return GitBundle.message("branches.there.are.outgoing.commits");
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
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
        new UpdateSelectedBranchAction(myProject, myRepositories, myBranchName, hasIncomingCommits()),
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
        super(GitBundle.messagePointer("branches.checkout"));
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

    private static class CheckoutWithRebaseAction extends CheckoutWithRebaseActionBase {

      CheckoutWithRebaseAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super(project, repositories, branchName);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitBrancher brancher = GitBrancher.getInstance(myProject);
        brancher.rebaseOnCurrent(myRepositories, myBranchName);
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
        super(ActionsBundle.messagePointer("action.Vcs.Push.text"));
        myProject = project;
        myRepositories = repositories;
        myBranchName = branchName;
        myHasCommitsToPush = hasCommitsToPush;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        disableActionIfAnyRepositoryIsFresh(e, myRepositories, GitBundle.message("action.not.possible.in.fresh.repo.push"));
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
        super(ActionsBundle.messagePointer("action.RenameAction.text"));
        myProject = project;
        myRepositories = repositories;
        myCurrentBranchName = currentBranchName;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        rename(myProject, myRepositories, myCurrentBranchName);
      }

      public static void rename(@NotNull Project project,
                                @NotNull List<? extends GitRepository> repositories,
                                @NotNull String currentBranchName) {
        GitNewBranchOptions options = new GitNewBranchDialog(project, repositories,
                                                             GitBundle.message("branches.rename.branch", currentBranchName),
                                                             currentBranchName,
                                                             false, false,
                                                             false, false, GitBranchOperationType.RENAME).showAndGetOptions();
        if (options != null) {
          GitBrancher brancher = GitBrancher.getInstance(project);
          brancher.renameBranch(currentBranchName, options.getName(), repositories);
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        disableActionIfAnyRepositoryIsFresh(e, myRepositories, GitBundle.message("action.not.possible.in.fresh.repo.rename.branch"));
      }
    }

    private static class DeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myBranchName;

      DeleteAction(Project project, List<? extends GitRepository> repositories, String branchName) {
        super(IdeBundle.messagePointer("action.delete"));
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

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName),
        new Separator(),
        new ShowDiffWithBranchAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new UpdateSelectedBranchAction(myProject, myRepositories, myBranchName, hasIncomingCommits()),
        new LocalBranchActions.PushBranchAction(myProject, myRepositories, myBranchName, hasOutgoingCommits()),
        new Separator(),
        new LocalBranchActions.RenameBranchAction(myProject, myRepositories, myBranchName),
      };
    }
  }

  /**
   * Actions available for remote branches
   */
  public static class RemoteBranchActions extends BranchActionGroup {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final @NlsSafe String myBranchName;
    @NotNull private final GitRepository mySelectedRepository;
    @NotNull private final GitBranchManager myGitBranchManager;

    public RemoteBranchActions(@NotNull Project project,
                               @NotNull List<? extends GitRepository> repositories,
                               @NotNull @NlsSafe String branchName,
                               @NotNull GitRepository selectedRepository) {

      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      myGitBranchManager = project.getService(GitBranchManager.class);
      getTemplatePresentation().setText(myBranchName, false); // no mnemonics
      setFavorite(myGitBranchManager.isFavorite(REMOTE, repositories.size() > 1 ? null : mySelectedRepository, myBranchName));
    }

    @Override
    public void toggle() {
      super.toggle();
      myGitBranchManager.setFavorite(REMOTE, myRepositories.size() > 1 ? null : mySelectedRepository, myBranchName, isFavorite());
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new CheckoutRemoteBranchAction(myProject, myRepositories, myBranchName),
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName),
        new CheckoutWithRebaseAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName),
        new ShowDiffWithBranchAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranchName),
        new MergeAction(myProject, myRepositories, myBranchName, false),
        new Separator(),
        new PullWithRebaseAction(myProject, myRepositories, myBranchName),
        new PullWithMergeAction(myProject, myRepositories, myBranchName),
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
        super(GitBundle.messagePointer("branches.checkout"));
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
        new GitBranchCheckoutOperation(project, repositories)
          .perform(remoteBranchName, new GitNewBranchOptions(suggestedLocalName, true, true));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        checkoutRemoteBranch(myProject, myRepositories, myRemoteBranchName);
      }

      private static void askNewBranchNameAndCheckout(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                                      @NotNull String remoteBranchName, @NotNull String suggestedLocalName) {
        //do not allow name conflicts
        GitNewBranchOptions options =
          new GitNewBranchDialog(project, repositories, GitBundle.message("branches.checkout.s", remoteBranchName), suggestedLocalName,
                                 false, true)
            .showAndGetOptions();
        if (options == null) return;
        GitBrancher brancher = GitBrancher.getInstance(project);
        brancher.checkoutNewBranchStartingFrom(options.getName(), remoteBranchName, options.shouldReset(), repositories, null);
      }
    }

    private static class CheckoutWithRebaseAction extends CheckoutWithRebaseActionBase {

      CheckoutWithRebaseAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super(project, repositories, branchName);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        GitRepository repository = myRepositories.get(0);
        GitRemoteBranch remoteBranch = Objects.requireNonNull(repository.getBranches().findRemoteBranch(myBranchName));
        String suggestedLocalName = remoteBranch.getNameForRemoteOperations();

        GitNewBranchOptions newBranchOptions = new GitNewBranchOptions(suggestedLocalName, false, true);
        // can have remote conflict if git-svn is used  - suggested local name will be equal to selected remote
        if (BRANCH_NAME_HASHING_STRATEGY.equals(myBranchName, suggestedLocalName)) {
          newBranchOptions = askBranchName(suggestedLocalName);
          if (newBranchOptions == null) return;
        }

        String localName = newBranchOptions.getName();
        Map<GitRepository, GitLocalBranch> conflictingLocalBranches = map2MapNotNull(myRepositories, r -> {
          GitLocalBranch local = r.getBranches().findLocalBranch(localName);
          return local != null ? Pair.create(r, local) : null;
        });

        if (hasTrackingConflicts(conflictingLocalBranches, myBranchName)) {
          newBranchOptions = askBranchName(localName);
          if (newBranchOptions == null) return;
        }

        GitCheckoutAndRebaseRemoteBranchWorkflow workflow = new GitCheckoutAndRebaseRemoteBranchWorkflow(myProject, myRepositories);
        workflow.execute(remoteBranch.getNameForLocalOperations(), newBranchOptions);
      }

      @Nullable
      private GitNewBranchOptions askBranchName(@NotNull String suggestedLocalName) {
        return new GitNewBranchDialog(myProject, myRepositories, GitBundle.message("branches.checkout.s", myBranchName), suggestedLocalName,
                                      false, true)
          .showAndGetOptions();
      }
    }

    private static class PullBranchBaseAction extends DumbAwareAction {

      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myRemoteBranchName;
      private final UpdateMethod myUpdateMethod;

      PullBranchBaseAction(@NotNull Project project,
                           @NotNull List<? extends GitRepository> repositories,
                           @NotNull String remoteBranchName,
                           UpdateMethod updateMethod) {
        myProject = project;
        myRepositories = repositories;
        myRemoteBranchName = remoteBranchName;
        myUpdateMethod = updateMethod;
      }

      private static Map<GitRepository, GitBranchPair> configureTarget(List<? extends GitRepository> repositories, String branchName) {

        Map<GitRepository, GitBranchPair> map = new LinkedHashMap<>();

        for (GitRepository repo : repositories) {
          GitLocalBranch currentBranch = repo.getCurrentBranch();
          GitRemoteBranch remoteBranch = repo.getBranches().findRemoteBranch(branchName);
          if (currentBranch != null && remoteBranch != null) {
            map.put(repo, new GitBranchPair(currentBranch, remoteBranch));
          }
        }

        return map;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        new GitUpdateExecutionProcess(myProject,
                                      myRepositories,
                                      configureTarget(myRepositories, myRemoteBranchName),
                                      myUpdateMethod, false)
          .execute();
      }
    }

    private static class PullWithMergeAction extends PullBranchBaseAction {

      PullWithMergeAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super(project, repositories, branchName, UpdateMethod.MERGE);
        Presentation presentation = getTemplatePresentation();

        Supplier<@Nls String> text = GitBundle.messagePointer("branches.action.pull.into.branch.using.merge",
                                                              getCurrentBranchTruncatedPresentation(project, repositories));
        presentation.setText(text);

        Supplier<@Nls String> description = GitBundle.messagePointer("branches.action.pull.into.branch.using.merge.description",
                                                                     getCurrentBranchFullPresentation(project, repositories));

        presentation.setDescription(description);
        addTooltipText(presentation, description.get());
      }
    }

    private static class PullWithRebaseAction extends PullBranchBaseAction {

      PullWithRebaseAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super(project, repositories, branchName, UpdateMethod.REBASE);
        Presentation presentation = getTemplatePresentation();

        Supplier<@Nls String> text = GitBundle.messagePointer("branches.action.pull.into.branch.using.rebase",
                                                              getCurrentBranchTruncatedPresentation(project, repositories));
        presentation.setText(text);
        Supplier<@Nls String> description = GitBundle.messagePointer("branches.action.pull.into.branch.using.rebase.description",
                                                                     getCurrentBranchFullPresentation(project, repositories));

        presentation.setDescription(description);
        addTooltipText(presentation, description.get());
      }
    }

    private static class RemoteDeleteAction extends DumbAwareAction {
      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myBranchName;

      RemoteDeleteAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
        super(IdeBundle.messagePointer("action.delete"));
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
      super(GitBundle.messagePointer("branches.new.branch.from.branch", getSelectedBranchTruncatedPresentation(project, branchName)));

      Supplier<@Nls String> description = GitBundle.messagePointer("branches.new.branch.from.branch.description",
                                                                   getSelectedBranchFullPresentation(branchName));
      getTemplatePresentation().setDescription(description);
      addTooltipText(getTemplatePresentation(), description.get());
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      disableActionIfAnyRepositoryIsFresh(e, myRepositories, DvcsBundle.message("action.not.possible.in.fresh.repo.new.branch"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      createOrCheckoutNewBranch(myProject, myRepositories, myBranchName + "^0",
                                GitBundle.message("action.Git.New.Branch.dialog.title", myBranchName));
    }
  }

  private abstract static class CheckoutWithRebaseActionBase extends DumbAwareAction {
    protected final Project myProject;
    protected final List<? extends GitRepository> myRepositories;
    protected final String myBranchName;

    CheckoutWithRebaseActionBase(@NotNull Project project,
                                 @NotNull List<? extends GitRepository> repositories,
                                 @NotNull String branchName) {
      super(GitBundle.messagePointer("branches.checkout.and.rebase.onto.current"));
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      String description = GitBundle.message("branches.checkout.and.rebase.onto.in.one.step",
                                             getSelectedBranchFullPresentation(myBranchName),
                                             getCurrentBranchFullPresentation(myProject, myRepositories),
                                             myBranchName);
      Presentation presentation = e.getPresentation();
      presentation.setDescription(description);
      addTooltipText(presentation, description);

      String text = GitBundle.message("branches.checkout.and.rebase.onto.branch",
                                      getCurrentBranchTruncatedPresentation(myProject, myRepositories));
      presentation.setText(text);
    }
  }

  private static class CompareAction extends DumbAwareAction {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    CompareAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
      super(GitBundle.messagePointer("branches.compare.with.current"));
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
      String description = GitBundle.message("branches.show.commits.in",
                                             getSelectedBranchFullPresentation(myBranchName),
                                             getCurrentBranchFullPresentation(myProject, myRepositories));
      Presentation presentation = e.getPresentation();
      presentation.setDescription(description);
      addTooltipText(presentation, description);

      String text = GitBundle.message("branches.compare.with.branch",
                                      getCurrentBranchTruncatedPresentation(myProject, myRepositories));
      presentation.setText(text);
    }
  }

  private static class ShowDiffWithBranchAction extends DumbAwareAction {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;

    ShowDiffWithBranchAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
      super(GitBundle.messagePointer("branches.show.diff.with.working.tree"));
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher.getInstance(myProject)
        .showDiffWithLocal(myBranchName, myRepositories);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(!new GitMultiRootBranchConfig(myRepositories).diverged());
      String description =
        GitBundle.message("branches.compare.the.current.working.tree.with", getSelectedBranchFullPresentation(myBranchName));
      e.getPresentation().setDescription(description);
      disableActionIfAnyRepositoryIsFresh(e, myRepositories, GitBundle.message("action.not.possible.in.fresh.repo.show.diff"));
    }
  }

  private static class MergeAction extends DumbAwareAction {

    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;
    private final boolean myLocalBranch;

    MergeAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull String branchName,
                boolean localBranch) {
      super(GitBundle.messagePointer("branches.merge.into.current"));
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      myLocalBranch = localBranch;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      String description = GitBundle.message("branches.merge.into",
                                             getSelectedBranchFullPresentation(myBranchName),
                                             getCurrentBranchFullPresentation(myProject, myRepositories));
      presentation.setDescription(description);
      addTooltipText(presentation, description);

      String name = GitBundle.message("branches.merge.into",
                                      getSelectedBranchTruncatedPresentation(myProject, myBranchName),
                                      getCurrentBranchTruncatedPresentation(myProject, myRepositories));
      presentation.setText(name);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.merge(myBranchName, deleteOnMerge(), myRepositories);
    }

    private GitBrancher.DeleteOnMergeOption deleteOnMerge() {
      if (myLocalBranch && !myBranchName.equals("master")) { // NON-NLS
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
      super(GitBundle.messagePointer("branches.rebase.current.onto.selected"));
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean isOnBranch = and(myRepositories, GitRepository::isOnBranch);

      String description = isOnBranch
                           ? GitBundle.message("branches.rebase.onto",
                                               getCurrentBranchFullPresentation(myProject, myRepositories),
                                               getSelectedBranchFullPresentation(myBranchName))
                           : GitBundle.message("branches.rebase.is.not.possible.in.the.detached.head.state");
      Presentation presentation = e.getPresentation();
      presentation.setDescription(description);
      addTooltipText(presentation, description);
      presentation.setEnabled(isOnBranch);

      String actionText = GitBundle.message(
        "branches.rebase.onto",
        getCurrentBranchTruncatedPresentation(myProject, myRepositories),
        getSelectedBranchTruncatedPresentation(myProject, myBranchName));
      presentation.setText(actionText);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      GitBrancher brancher = GitBrancher.getInstance(myProject);
      brancher.rebase(myRepositories, myBranchName);
    }
  }

  private static class UpdateSelectedBranchAction extends DumbAwareAction implements CustomIconProvider {
    protected final Project myProject;
    protected final List<? extends GitRepository> myRepositories;
    protected final String myBranchName;
    protected final List<String> myBranchNameList;
    protected final boolean myHasIncoming;

    UpdateSelectedBranchAction(@NotNull Project project,
                               @NotNull List<? extends GitRepository> repositories,
                               @NotNull String branchName,
                               boolean hasIncoming) {
      super(GitBundle.messagePointer("branches.update"));
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      myBranchNameList = Collections.singletonList(branchName);
      myHasIncoming = hasIncoming;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      if (!hasRemotes(myProject)) {
        presentation.setEnabledAndVisible(false);
        return;
      }
      String branchPresentation = getSelectedBranchFullPresentation(myBranchName);
      String description = GitBundle.message("action.Git.Update.Selected.description",
                                             myBranchNameList.size(),
                                             GitVcsSettings.getInstance(myProject).getUpdateMethod().getMethodName().toLowerCase(Locale.ROOT));
      presentation.setDescription(description);
      if (GitFetchSupport.fetchSupport(myProject).isFetchRunning()) {
        presentation.setEnabled(false);
        presentation.setDescription(GitBundle.message("branches.update.is.already.running"));
        return;
      }

      boolean trackingInfosExist = isTrackingInfosExist(myBranchNameList, myRepositories);
      presentation.setEnabled(trackingInfosExist);
      if (!trackingInfosExist) {
        presentation.setDescription(GitBundle.message("branches.tracking.branch.doesn.t.configured.for.s", branchPresentation));
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      updateBranches(myProject, myRepositories, myBranchNameList);
    }

    @Nullable
    @Override
    public Icon getRightIcon() {
      return myHasIncoming ? DvcsImplIcons.Incoming : null;
    }
  }

  static class TagActions extends BranchActionGroup {
    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myTagName;

    TagActions(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull @NlsSafe String tagName) {
      myProject = project;
      myRepositories = repositories;
      myTagName = tagName;
      getTemplatePresentation().setText(tagName, false); // no mnemonics
      setIcons(EmptyIcon.ICON_16, EmptyIcon.ICON_16, EmptyIcon.ICON_16, EmptyIcon.ICON_16); // no favorites
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new DeleteTagAction(myProject, myRepositories, myTagName)
      };
    }

    private static class DeleteTagAction extends DumbAwareAction {
      private final Project myProject;
      private final List<? extends GitRepository> myRepositories;
      private final String myTagName;

      DeleteTagAction(Project project, List<? extends GitRepository> repositories, String tagName) {
        super(IdeBundle.messagePointer("button.delete"));
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

  @NlsSafe
  @NotNull
  public static String getCurrentBranchFullPresentation(@NotNull Project project,
                                                        @NotNull Collection<? extends GitRepository> repositories) {
    return getCurrentBranchPresentation(project, repositories, false);
  }

  @NlsSafe
  @NotNull
  public static String getCurrentBranchTruncatedPresentation(@NotNull Project project,
                                                             @NotNull Collection<? extends GitRepository> repositories) {
    return getCurrentBranchPresentation(project, repositories, true);
  }

  @Nls
  @NotNull
  private static String getCurrentBranchPresentation(@NotNull Project project,
                                                     @NotNull Collection<? extends GitRepository> repositories,
                                                     boolean truncateBranchName) {
    Set<String> currentBranches = map2SetNotNull(repositories,
                                                 repo -> repo.isFresh() ? repo.getCurrentBranchName() :
                                                         notNull(repo.getCurrentBranchName(),
                                                                 getShortHash(Objects.requireNonNull(repo.getCurrentRevision()))));

    if (currentBranches.size() == 1) {
      String fullBranchName = currentBranches.iterator().next();
      return truncateBranchName
             ? getCurrentBranchTruncatedName(fullBranchName, project)
             : wrapWithQuotes(fullBranchName);
    }
    return GitBundle.message("branches.current.branch");
  }

  @NlsSafe
  @NotNull
  public static String getSelectedBranchFullPresentation(@NlsSafe @NotNull String branchName) {
    return wrapWithQuotes(branchName);
  }

  private static final int MAX_BRANCH_NAME_LENGTH = 40;
  private static final int BRANCH_NAME_LENGHT_DELTA = 4;
  private static final int BRANCH_NAME_SUFFIX_LENGTH = 5;

  @NlsSafe
  @NotNull
  private static String getCurrentBranchTruncatedName(@NlsSafe @NotNull String branchName,
                                                      @NotNull Project project) {
    return showFullBranchNamesInsteadOfCurrentSelected()
           ? wrapWithQuotes(StringUtil.escapeMnemonics(truncateBranchName(branchName, project)))
           : GitBundle.message("branches.current.branch.name");
  }

  @NlsSafe
  @NotNull
  public static String getSelectedBranchTruncatedPresentation(@NotNull Project project,
                                                              @NlsSafe @NotNull String branchName) {
    return showFullBranchNamesInsteadOfCurrentSelected()
           ? wrapWithQuotes(StringUtil.escapeMnemonics(truncateBranchName(branchName, project)))
           : GitBundle.message("branches.selected.branch.name");
  }

  private static boolean showFullBranchNamesInsteadOfCurrentSelected() {
    return Registry.is("git.show.full.branch.name.instead.current.selected");
  }

  @NlsSafe
  @NotNull
  static String truncateBranchName(@NotNull @NlsSafe String branchName, @NotNull Project project) {
    int branchNameLength = branchName.length();

    if (branchNameLength <= MAX_BRANCH_NAME_LENGTH + BRANCH_NAME_LENGHT_DELTA) {
      return branchName;
    }

    IssueNavigationConfiguration issueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
    List<IssueNavigationConfiguration.LinkMatch> issueMatches = issueNavigationConfiguration.findIssueLinks(branchName);
    if (issueMatches.size() != 0) {
      // never truncate the first occurrence of the issue id
      IssueNavigationConfiguration.LinkMatch firstMatch = issueMatches.get(0);
      TextRange firstMatchRange = firstMatch.getRange();
      return truncateAndSaveIssueId(firstMatchRange, branchName, MAX_BRANCH_NAME_LENGTH, BRANCH_NAME_SUFFIX_LENGTH,
                                    BRANCH_NAME_LENGHT_DELTA);
    }

    return StringUtil.shortenTextWithEllipsis(branchName,
                                              MAX_BRANCH_NAME_LENGTH,
                                              BRANCH_NAME_SUFFIX_LENGTH, true);
  }

  @NlsSafe
  @NotNull
  static String truncateAndSaveIssueId(@NotNull TextRange issueIdRange,
                                       @NotNull String branchName,
                                       int maxBranchNameLength,
                                       int suffixLength, int delta) {
    String truncatedByDefault = StringUtil.shortenTextWithEllipsis(branchName,
                                                                   maxBranchNameLength,
                                                                   suffixLength, true);
    String issueId = issueIdRange.substring(branchName);

    if (truncatedByDefault.contains(issueId)) return truncatedByDefault;

    try {
      int branchNameLength = branchName.length();
      int endOffset = issueIdRange.getEndOffset();
      int startOffset = issueIdRange.getStartOffset();

      // suffix intersects with the issue id
      if (endOffset >= branchNameLength - suffixLength - delta) {
        return StringUtil.shortenTextWithEllipsis(branchName,
                                                  maxBranchNameLength,
                                                  branchNameLength - startOffset, true);
      }

      String suffix = branchName.substring(branchNameLength - suffixLength);
      int prefixLength = maxBranchNameLength - suffixLength - issueId.length();

      String prefixAndIssue;
      if (Math.abs(startOffset - prefixLength) <= delta) {
        prefixAndIssue = branchName.substring(0, endOffset);
      }
      else {
        String prefix = branchName.substring(0, prefixLength);
        prefixAndIssue = prefix + StringUtil.ELLIPSIS + issueId;
      }

      return prefixAndIssue + StringUtil.ELLIPSIS + suffix;
    }
    catch (Throwable e) {
      return truncatedByDefault;
    }
  }

  @NlsSafe
  @NotNull
  private static String wrapWithQuotes(@NlsSafe @NotNull String branchName) {
    return "'" + branchName + "'";
  }

  /**
   * Adds a tooltip to action in the branches popup
   *
   * @see com.intellij.ui.popup.ActionStepBuilder#appendAction
   */
  public static void addTooltipText(Presentation presentation, @NlsContexts.Tooltip String tooltipText) {
    presentation.putClientProperty(JComponent.TOOL_TIP_TEXT_KEY, tooltipText);
  }
}
