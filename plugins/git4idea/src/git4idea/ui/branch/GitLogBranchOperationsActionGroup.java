// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.actions.GitSingleCommitActionGroup;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.log.GitRefManager;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static git4idea.ui.branch.GitBranchPopupActions.LocalBranchActions;
import static git4idea.ui.branch.GitBranchPopupActions.RemoteBranchActions;

public final class GitLogBranchOperationsActionGroup extends GitSingleCommitActionGroup {
  private static final int MAX_BRANCH_GROUPS = 2;
  private static final int MAX_TAG_GROUPS = 1;

  public GitLogBranchOperationsActionGroup() {
    setPopup(false);
  }

  @Override
  public AnAction @NotNull [] getChildren(@NotNull AnActionEvent e,
                                          @NotNull Project project,
                                          @NotNull GitRepository root,
                                          @NotNull CommitId commit) {
    VcsLogUi logUI = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    List<VcsRef> refs = e.getData(VcsLogDataKeys.VCS_LOG_REFS);
    if (logUI == null || refs == null) {
      return EMPTY_ARRAY;
    }

    List<VcsRef> branchRefs = ContainerUtil.filter(refs, ref -> {
      if (ref.getType() == GitRefManager.LOCAL_BRANCH) {
        return !ref.getName().equals(root.getCurrentBranchName());
      }
      if (ref.getType() == GitRefManager.REMOTE_BRANCH) return true;
      return false;
    });
    List<VcsRef> tagRefs = ContainerUtil.filter(refs, ref -> ref.getType() == GitRefManager.TAG);

    VcsLogProvider provider = logUI.getDataPack().getLogProviders().get(root.getRoot());
    if (provider != null) {
      VcsLogRefManager refManager = provider.getReferenceManager();
      Comparator<VcsRef> comparator = refManager.getLabelsOrderComparator();
      branchRefs = ContainerUtil.sorted(branchRefs, comparator);
      tagRefs = ContainerUtil.sorted(tagRefs, comparator);
    }


    List<AnAction> groups = new ArrayList<>();

    GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
    List<GitRepository> allRepositories = repositoryManager.getRepositories();

    if (!branchRefs.isEmpty()) {
      GitVcsSettings settings = GitVcsSettings.getInstance(project);
      boolean showBranchesPopup = branchRefs.size() > MAX_BRANCH_GROUPS;

      Set<GitBranch> commonBranches = new HashSet<>();
      commonBranches.addAll(GitBranchUtil.getCommonLocalBranches(allRepositories));
      commonBranches.addAll(GitBranchUtil.getCommonRemoteBranches(allRepositories));

      List<AnAction> branchActionGroups = new ArrayList<>();
      for (VcsRef ref : branchRefs) {
        AnAction group = createBranchGroup(project, ref, root, allRepositories, commonBranches, settings, showBranchesPopup);
        ContainerUtil.addIfNotNull(branchActionGroups, group);
      }

      DefaultActionGroup branchesGroup = new DefaultActionGroup(GitBundle.message("branches.branches"), branchActionGroups);
      branchesGroup.setPopup(showBranchesPopup);
      groups.add(branchesGroup);
    }
    else {
      GitRebaseOntoCommitAction rebaseOntoCommitAction = new GitRebaseOntoCommitAction(project, root, commit);
      groups.add(rebaseOntoCommitAction);
    }

    if (!tagRefs.isEmpty()) {
      boolean showTagsPopup = tagRefs.size() > MAX_TAG_GROUPS;

      List<AnAction> tagActionGroups = new ArrayList<>();
      for (VcsRef ref : tagRefs) {
        tagActionGroups.add(createTagGroup(project, ref, root, showTagsPopup));
      }

      DefaultActionGroup tagsGroup = new DefaultActionGroup(GitBundle.message("branches.tags"), tagActionGroups);
      tagsGroup.setPopup(showTagsPopup);
      groups.add(tagsGroup);
    }

    return groups.toArray(EMPTY_ARRAY);
  }

  private static @Nullable AnAction createBranchGroup(@NotNull Project project,
                                                      @NotNull VcsRef ref,
                                                      @NotNull GitRepository repository,
                                                      @NotNull List<? extends GitRepository> allRepositories,
                                                      @NotNull Set<GitBranch> commonBranches,
                                                      @NotNull GitVcsSettings settings,
                                                      boolean showBranchesPopup) {
    // ref.getName() for GitRefManager.REMOTE_BRANCH is GitRemoteBranch.getNameForLocalOperations
    GitBranch branch = ref.getType() == GitRefManager.LOCAL_BRANCH
                       ? new GitLocalBranch(ref.getName())
                       : GitUtil.parseRemoteBranch(ref.getName(), repository.getRemotes());

    List<AnAction> actions = new ArrayList<>(3);

    boolean isSyncBranch = settings.getSyncSetting() != DvcsSyncSettings.Value.DONT_SYNC &&
                           allRepositories.size() > 1 &&
                           commonBranches.contains(branch);
    if (isSyncBranch) {
      ActionGroup allReposActions = createBranchActions(project, allRepositories, branch, repository);
      if (allReposActions != null) {
        allReposActions.getTemplatePresentation().setText(GitBundle.message("in.branches.all.repositories"));
        allReposActions.setPopup(true);
        actions.add(allReposActions);
        actions.add(Separator.getInstance());
      }
    }

    ActionGroup singleRepoActions = createBranchActions(project, Collections.singletonList(repository), branch, repository);
    if (singleRepoActions == null) return null;
    singleRepoActions.setPopup(false);
    actions.add(singleRepoActions);

    String text = showBranchesPopup ? ref.getName() : GitBundle.message("branches.branch.0", ref.getName());
    ActionGroup group = new DefaultActionGroup(actions);
    group.getTemplatePresentation().setText(text, false);
    group.setPopup(true);
    return group;
  }

  private static @NotNull AnAction createTagGroup(@NotNull Project project,
                                                  @NotNull VcsRef ref,
                                                  @NotNull GitRepository repository,
                                                  boolean showTagsPopup) {
    ActionGroup singleRepoActions = createTagActions(project, Collections.singletonList(repository), ref);
    singleRepoActions.setPopup(false);

    String text = showTagsPopup ? ref.getName() : GitBundle.message("branches.tag.0", ref.getName());
    ActionGroup group = new DefaultActionGroup(singleRepoActions);
    group.getTemplatePresentation().setText(text, false);
    group.setPopup(true);
    return group;
  }

  private static @Nullable ActionGroup createBranchActions(@NotNull Project project,
                                                           @NotNull List<? extends GitRepository> repositories,
                                                           @NotNull GitBranch branch,
                                                           @NotNull GitRepository selectedRepository) {
    if (branch instanceof GitLocalBranch) {
      for (GitRepository repository : repositories) {
        if (!repository.getBranches().getLocalBranches().contains(branch)) return null;
      }

      return new LocalBranchActions(project, repositories, (GitLocalBranch)branch, selectedRepository);
    }
    else if (branch instanceof GitRemoteBranch) {
      for (GitRepository repository : repositories) {
        if (!repository.getBranches().getRemoteBranches().contains(branch)) return null;
      }

      return new RemoteBranchActions(project, repositories, (GitRemoteBranch)branch, selectedRepository);
    }
    else {
      return null;
    }
  }

  private static @NotNull ActionGroup createTagActions(@NotNull Project project,
                                                       @NotNull List<? extends GitRepository> repositories,
                                                       @NotNull VcsRef ref) {
    return new GitBranchPopupActions.TagActions(project, repositories, ref.getName());
  }
}
