// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.ui.BranchActionGroup;
import com.intellij.dvcs.ui.NewBranchAction;
import com.intellij.dvcs.ui.PopupElementWithAdditionalInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.vcs.git.actions.GitSingleRefActions;
import com.intellij.vcs.git.ui.GitBranchPresentation;
import git4idea.*;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBrancher;
import git4idea.config.GitSharedSettings;
import git4idea.i18n.GitBundle;
import git4idea.remote.hosting.GitRemoteBranchesUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortHash;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static com.intellij.util.containers.ContainerUtil.map2SetNotNull;
import static git4idea.GitUtil.HEAD;
import static git4idea.branch.GitBranchType.LOCAL;
import static git4idea.branch.GitBranchType.REMOTE;
import static git4idea.ui.branch.GitBranchActionsUtilKt.createOrCheckoutNewBranch;

public final class GitBranchPopupActions {
  private static final int MAX_BRANCH_NAME_LENGTH = 40;

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
    return GitBranchPresentation.truncateBranchName(project, branchName, MAX_BRANCH_NAME_LENGTH);
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

    public @NotNull String getBranchName() {
      return myBranchName;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return getSingleBranchActions(myBranch, myRepositories, mySelectedRepository, e);
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
  }

  /**
   * Actions available for remote branches
   */
  public static class RemoteBranchActions extends BranchActionGroup {
    private final List<? extends GitRepository> myRepositories;
    private final @NlsSafe String myBranchName;
    private final @NlsSafe GitRemoteBranch myBranch;
    private final @NotNull GitRepository mySelectedRepository;
    private final @NotNull GitBranchManager myGitBranchManager;

    public RemoteBranchActions(@NotNull Project project,
                               @NotNull List<? extends GitRepository> repositories,
                               @NotNull GitRemoteBranch branch,
                               @NotNull GitRepository selectedRepository) {
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
      return getSingleBranchActions(myBranch, myRepositories, mySelectedRepository, e);
    }

    /**
     * @deprecated use {@link GitRemoteBranchesUtil}
     */
    @Deprecated(forRemoval = true)
    public static final class CheckoutRemoteBranchAction {
      /**
       * @deprecated use {@link GitRemoteBranchesUtil#checkoutRemoteBranch(Project, List, String, String, Runnable)}
       */
      @Deprecated(forRemoval = true)
      @RequiresEdt
      public static void checkoutRemoteBranch(@NotNull Project project, @NotNull List<? extends GitRepository> repositories,
                                              @NotNull String remoteBranchName) {
        GitRemoteBranchesUtil.checkoutRemoteBranch(project, repositories, remoteBranchName);
      }
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

  private static AnAction @NotNull [] getSingleBranchActions(GitBranch branch,
                                                             List<? extends GitRepository> repositories,
                                                             @NotNull GitRepository selectedRepository,
                                                             @Nullable AnActionEvent e) {
    AnAction[] actions = GitSingleRefActions.getSingleRefActionGroup().getChildren(e);
    return map2Array(actions,
                     AnAction.class,
                     action -> GitBranchActionWrapper.tryWrap(action, branch, selectedRepository, repositories));
  }
}
