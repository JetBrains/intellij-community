// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.ui.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EmptyIcon;
import git4idea.*;
import git4idea.actions.branch.GitBranchActionsUtil;
import git4idea.branch.*;
import git4idea.config.GitSharedSettings;
import git4idea.config.GitVcsSettings;
import git4idea.config.UpdateMethod;
import git4idea.fetch.GitFetchSupport;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushSource;
import git4idea.repo.GitRepository;
import git4idea.update.GitUpdateExecutionProcess;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.dvcs.DvcsUtil.disableActionIfAnyRepositoryIsFresh;
import static com.intellij.dvcs.DvcsUtil.getShortHash;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.GitReference.BRANCH_NAME_HASHING_STRATEGY;
import static git4idea.GitUtil.HEAD;
import static git4idea.branch.GitBranchType.LOCAL;
import static git4idea.branch.GitBranchType.REMOTE;
import static git4idea.ui.branch.GitBranchActionsUtilKt.*;

public final class GitBranchPopupActions {

  public static final @NonNls String EXPERIMENTAL_BRANCH_POPUP_ACTION_GROUP = "Git.Experimental.Branch.Popup.Actions";

  private static final int MAX_BRANCH_NAME_LENGTH = 40;
  public static final int BRANCH_NAME_LENGTH_DELTA = 4;
  public static final int BRANCH_NAME_SUFFIX_LENGTH = 5;

  private GitBranchPopupActions() { }

  public static @NlsSafe @NotNull String getCurrentBranchFullPresentation(@NotNull Project project,
                                                                          @NotNull Collection<? extends GitRepository> repositories) {
    return getCurrentBranchPresentation(project, repositories, false);
  }

  public static @NlsSafe @NotNull String getCurrentBranchTruncatedPresentation(@NotNull Project project,
                                                                               @NotNull Collection<? extends GitRepository> repositories) {
    return getCurrentBranchPresentation(project, repositories, true);
  }

  private static @Nls @NotNull String getCurrentBranchPresentation(@NotNull Project project,
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

  public static @NlsSafe @NotNull String getSelectedBranchFullPresentation(@NlsSafe @NotNull String branchName) {
    return wrapWithQuotes(branchName);
  }

  private static @NlsSafe @NotNull String getCurrentBranchTruncatedName(@NlsSafe @NotNull String branchName,
                                                                        @NotNull Project project) {
    return wrapWithQuotes(StringUtil.escapeMnemonics(truncateBranchName(branchName, project)));
  }

  public static @NlsSafe @NotNull String getSelectedBranchTruncatedPresentation(@NotNull Project project,
                                                                                @NlsSafe @NotNull String branchName) {
    return wrapWithQuotes(StringUtil.escapeMnemonics(truncateBranchName(branchName, project)));
  }

  public static @NlsSafe @NotNull String truncateBranchName(@NotNull @NlsSafe String branchName, @NotNull Project project) {
    return truncateBranchName(project, branchName,
                              MAX_BRANCH_NAME_LENGTH, BRANCH_NAME_SUFFIX_LENGTH, BRANCH_NAME_LENGTH_DELTA);
  }

  public static @NlsSafe @NotNull String truncateBranchName(@NotNull Project project, @NotNull @NlsSafe String branchName,
                                                            int maxBranchNameLength, int suffixLength, int delta) {
    int branchNameLength = branchName.length();

    if (branchNameLength <= maxBranchNameLength + delta) {
      return branchName;
    }

    IssueNavigationConfiguration issueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);
    List<IssueNavigationConfiguration.LinkMatch> issueMatches = issueNavigationConfiguration.findIssueLinks(branchName);
    int affectedMaxBranchNameLength = maxBranchNameLength - StringUtil.ELLIPSIS.length();
    if (!issueMatches.isEmpty()) {
      // never truncate the first occurrence of the issue id
      IssueNavigationConfiguration.LinkMatch firstMatch = issueMatches.get(0);
      TextRange firstMatchRange = firstMatch.getRange();
      return truncateAndSaveIssueId(firstMatchRange, branchName, affectedMaxBranchNameLength, suffixLength, delta);
    }

    if (affectedMaxBranchNameLength - suffixLength - StringUtil.ELLIPSIS.length() < 0) {
      return branchName;
    }

    return StringUtil.shortenTextWithEllipsis(branchName, affectedMaxBranchNameLength, suffixLength, true);
  }

  private static @NlsSafe @NotNull String truncateAndSaveIssueId(@NotNull TextRange issueIdRange,
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

  private static @NlsSafe @NotNull String wrapWithQuotes(@NlsSafe @NotNull String branchName) {
    return "'" + branchName + "'";
  }

  /**
   * Adds a tooltip to action in the branches popup
   *
   * @see com.intellij.ui.popup.ActionStepBuilder#appendAction
   */
  public static void addTooltipText(Presentation presentation, @NlsContexts.Tooltip String tooltipText) {
    presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, tooltipText);
  }

  /**
   * @deprecated only used externally
   */
  @Deprecated
  public static class GitNewBranchAction extends NewBranchAction<GitRepository> {

    public GitNewBranchAction(@NotNull Project project, @NotNull List<GitRepository> repositories) {
      super(project, repositories);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      createOrCheckoutNewBranch(myProject, myRepositories, HEAD,
                                GitBundle.message("branches.create.new.branch.dialog.title"),
                                MultiRootBranches.getCommonCurrentBranch(myRepositories));
    }
  }

  /**
   * Actions available for local branches.
   */
  public static class LocalBranchActions extends BranchActionGroup implements PopupElementWithAdditionalInfo {

    protected final Project myProject;
    protected final List<GitRepository> myRepositories;
    protected final @NlsSafe String myBranchName;
    protected final GitLocalBranch myBranch;
    private final @NotNull GitRepository mySelectedRepository;
    private final GitBranchManager myGitBranchManager;
    private final @NotNull GitBranchIncomingOutgoingManager myIncomingOutgoingManager;

    public LocalBranchActions(@NotNull Project project,
                              @NotNull List<? extends GitRepository> repositories,
                              @NotNull GitLocalBranch branch,
                              @NotNull GitRepository selectedRepository) {
      myProject = project;
      myRepositories = Collections.unmodifiableList(repositories);
      myBranch = branch;
      myBranchName = branch.getName();
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

    public @NotNull String getBranchName() {
      return myBranchName;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new CheckoutAction(myProject, myRepositories, myBranchName),
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName, false),
        new CheckoutWithRebaseAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName),
        new ShowDiffWithBranchAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranch),
        new MergeAction(myProject, myRepositories, myBranch),
        new Separator(),
        new UpdateSelectedBranchAction(myProject, myRepositories, myBranchName, hasIncomingCommits()),
        new PushBranchAction(myProject, myRepositories, myBranchName, hasOutgoingCommits()),
        new Separator(),
        new RenameBranchAction(myProject, myRepositories, myBranchName),
        new DeleteAction(myProject, myRepositories, myBranchName)
      };
    }

    @Override
    public @Nullable String getInfoText() {
      return new GitMultiRootBranchConfig(myRepositories).getTrackedBranch(myBranchName);
    }

    @Override
    public void toggle() {
      super.toggle();
      myGitBranchManager.setFavorite(LOCAL, chooseRepo(), myBranchName, isFavorite());
    }

    private @Nullable GitRepository chooseRepo() {
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

    public static @Nls(capitalization = Nls.Capitalization.Sentence) @Nullable String constructIncomingOutgoingTooltip(boolean incoming, boolean outgoing) {
      if (!incoming && !outgoing) return null;

      if (incoming && outgoing) {
        return GitBundle.message("branches.there.are.incoming.and.outgoing.commits");
      }
      if (incoming) {
        return GitBundle.message("branches.there.are.incoming.commits");
      }
      return GitBundle.message("branches.there.are.outgoing.commits");
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
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
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

      @Override
      public @Nullable Icon getRightIcon() {
        return myHasCommitsToPush ? DvcsImplIcons.Outgoing : null;
      }
    }

    public static class RenameBranchAction extends DumbAwareAction {
      private final @NotNull Project myProject;
      private final @NotNull List<? extends GitRepository> myRepositories;
      private final @NotNull String myCurrentBranchName;

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

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        disableActionIfAnyRepositoryIsFresh(e, myRepositories, GitBundle.message("action.not.possible.in.fresh.repo.rename.branch"));
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
                                @NotNull GitLocalBranch branch,
                                @NotNull GitRepository selectedRepository) {
      super(project, repositories, branch, selectedRepository);
      setIcons(DvcsImplIcons.CurrentBranchFavoriteLabel, DvcsImplIcons.CurrentBranchLabel, AllIcons.Nodes.Favorite,
               AllIcons.Nodes.NotFavoriteOnHover);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName, false),
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
    private final @NlsSafe GitRemoteBranch myBranch;
    private final @NotNull GitRepository mySelectedRepository;
    private final @NotNull GitBranchManager myGitBranchManager;

    public RemoteBranchActions(@NotNull Project project,
                               @NotNull List<? extends GitRepository> repositories,
                               @NotNull GitRemoteBranch branch,
                               @NotNull GitRepository selectedRepository) {

      myProject = project;
      myRepositories = repositories;
      myBranch = branch;
      myBranchName = branch.getName();
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
        new CheckoutAsNewBranch(myProject, myRepositories, myBranchName, true),
        new CheckoutWithRebaseAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new CompareAction(myProject, myRepositories, myBranchName),
        new ShowDiffWithBranchAction(myProject, myRepositories, myBranchName),
        new Separator(),
        new RebaseAction(myProject, myRepositories, myBranch),
        new MergeAction(myProject, myRepositories, myBranch),
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

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        checkoutRemoteBranch(myProject, myRepositories, myRemoteBranchName);
      }

      @RequiresEdt
      public static void checkoutRemoteBranch(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                              @NotNull String remoteBranchName) {
        GitRepository repository = repositories.get(0);
        GitRemoteBranch remoteBranch = Objects.requireNonNull(repository.getBranches().findRemoteBranch(remoteBranchName));
        String suggestedLocalName = remoteBranch.getNameForRemoteOperations();
        checkoutRemoteBranch(project, repositories, remoteBranchName, suggestedLocalName, null);
      }

      @RequiresEdt
      public static void checkoutRemoteBranch(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                              @NotNull String remoteBranchName, @NotNull String suggestedLocalName, @Nullable Runnable callInAwtLater) {
        // can have remote conflict if git-svn is used  - suggested local name will be equal to selected remote
        if (BRANCH_NAME_HASHING_STRATEGY.equals(remoteBranchName, suggestedLocalName)) {
          askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName, callInAwtLater);
          return;
        }

        Map<GitRepository, GitLocalBranch> conflictingLocalBranches = map2MapNotNull(repositories, r -> {
          GitLocalBranch local = r.getBranches().findLocalBranch(suggestedLocalName);
          return local != null ? Pair.create(r, local) : null;
        });

        if (hasTrackingConflicts(conflictingLocalBranches, remoteBranchName)) {
          askNewBranchNameAndCheckout(project, repositories, remoteBranchName, suggestedLocalName, callInAwtLater);
          return;
        }
        new GitBranchCheckoutOperation(project, repositories)
          .perform(remoteBranchName, new GitNewBranchOptions(suggestedLocalName, true, true), callInAwtLater);
      }

      @RequiresEdt
      private static void askNewBranchNameAndCheckout(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                                      @NotNull String remoteBranchName, @NotNull String suggestedLocalName,
                                                      @Nullable Runnable callInAwtLater) {
        //do not allow name conflicts
        GitNewBranchOptions options =
          new GitNewBranchDialog(project, repositories, GitBundle.message("branches.checkout.s", remoteBranchName), suggestedLocalName,
                                 false, true)
            .showAndGetOptions();
        if (options == null) return;
        GitBrancher brancher = GitBrancher.getInstance(project);
        brancher.checkoutNewBranchStartingFrom(options.getName(), remoteBranchName, options.shouldReset(), repositories, callInAwtLater);
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

      private @Nullable GitNewBranchOptions askBranchName(@NotNull String suggestedLocalName) {
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

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        new GitUpdateExecutionProcess(myProject,
                                      myRepositories,
                                      configureTarget(myRepositories, myRemoteBranchName),
                                      myUpdateMethod, false)
          .execute();
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
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
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
    private final boolean myIsRemote;

    CheckoutAsNewBranch(@NotNull Project project,
                        @NotNull List<? extends GitRepository> repositories,
                        @NotNull String branchName,
                        boolean isRemote) {
      super(GitBundle.messagePointer("branches.new.branch.from.branch", getSelectedBranchTruncatedPresentation(project, branchName)));

      Supplier<@Nls String> description = GitBundle.messagePointer("branches.new.branch.from.branch.description",
                                                                   getSelectedBranchFullPresentation(branchName));
      getTemplatePresentation().setDescription(description);
      addTooltipText(getTemplatePresentation(), description.get());
      myProject = project;
      myRepositories = repositories;
      myBranchName = branchName;
      myIsRemote = isRemote;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      disableActionIfAnyRepositoryIsFresh(e, myRepositories, DvcsBundle.message("action.not.possible.in.fresh.repo.new.branch"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      createOrCheckoutNewBranch(myProject, myRepositories, myBranchName + "^0",
                                GitBundle.message("action.Git.New.Branch.dialog.title", myBranchName),
                                GitBranchActionsUtil.calculateNewBranchInitialName(myBranchName, myIsRemote));
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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

    private final @NotNull GitReference myBranch;

    MergeAction(@NotNull Project project,
                @NotNull List<? extends GitRepository> repositories,
                @NotNull GitReference reference) {
      super(GitBundle.messagePointer("branches.merge.into.current"));
      myProject = project;
      myRepositories = repositories;
      myBranch = reference;
      myBranchName = reference.getName();
      myLocalBranch = (reference instanceof GitBranch branch) && !branch.isRemote();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
      brancher.merge(myBranch, deleteOnMerge(myProject),
                     myRepositories);
    }

    private GitBrancher.DeleteOnMergeOption deleteOnMerge(Project project) {
      if (myLocalBranch && !GitSharedSettings.getInstance(project).isBranchProtected(myBranchName)) {
        return GitBrancher.DeleteOnMergeOption.PROPOSE;
      }
      return GitBrancher.DeleteOnMergeOption.NOTHING;
    }
  }

  private static class RebaseAction extends DumbAwareAction {
    private final Project myProject;
    private final List<? extends GitRepository> myRepositories;
    private final String myBranchName;
    private final @NotNull GitReference myReference;

    RebaseAction(@NotNull Project project, @NotNull List<? extends GitRepository> repositories, @NotNull GitReference reference) {
      super(GitBundle.messagePointer("branches.rebase.current.onto.selected"));
      myProject = project;
      myRepositories = repositories;
      myBranchName = reference.getName();
      myReference = reference;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
      GitBrancher.getInstance(myProject).rebase(myRepositories, myReference.getFullName());
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
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
                                             GitVcsSettings.getInstance(myProject).getUpdateMethod().getMethodName()
                                               .toLowerCase(Locale.ROOT));
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

    @Override
    public @Nullable Icon getRightIcon() {
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
        new MergeAction(myProject, myRepositories, new GitTag(myTagName)),
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
}
