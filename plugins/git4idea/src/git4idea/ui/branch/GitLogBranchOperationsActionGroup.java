/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import git4idea.GitLocalBranch;
import git4idea.GitReference;
import git4idea.GitRemoteBranch;
import git4idea.actions.GitSingleCommitActionGroup;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.log.GitRefManager;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.GitBranchPopupActions.LocalBranchActions;
import git4idea.ui.branch.GitBranchPopupActions.RemoteBranchActions;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GitLogBranchOperationsActionGroup extends GitSingleCommitActionGroup {
  private static final int MAX_BRANCH_GROUPS = 2;
  private static final int MAX_TAG_GROUPS = 1;

  public GitLogBranchOperationsActionGroup() {
    setPopup(false);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@NotNull AnActionEvent e,
                                @NotNull Project project,
                                @NotNull VcsLog log,
                                @NotNull GitRepository root,
                                @NotNull CommitId commit) {
    VcsLogUi logUI = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    List<VcsRef> refs = e.getData(VcsLogDataKeys.VCS_LOG_REFS);
    if (logUI == null || refs == null) {
      return AnAction.EMPTY_ARRAY;
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
      ContainerUtil.sort(branchRefs, comparator);
      ContainerUtil.sort(tagRefs, comparator);
    }


    List<AnAction> groups = new ArrayList<>();

    if (!branchRefs.isEmpty()) {
      GitVcsSettings settings = GitVcsSettings.getInstance(project);
      boolean showBranchesPopup = branchRefs.size() > MAX_BRANCH_GROUPS;

      GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
      List<GitRepository> allRepositories = repositoryManager.getRepositories();

      Set<String> commonBranches = new THashSet<>(GitReference.BRANCH_NAME_HASHING_STRATEGY);
      for (GitLocalBranch branch : GitBranchUtil.getCommonLocalBranches(allRepositories)) {
        commonBranches.add(branch.getName());
      }
      for (GitRemoteBranch branch : GitBranchUtil.getCommonRemoteBranches(allRepositories)) {
        commonBranches.add(branch.getName());
      }

      List<AnAction> branchActionGroups = new ArrayList<>();
      for (VcsRef ref : branchRefs) {
        branchActionGroups.add(createBranchGroup(project, ref, root, allRepositories, commonBranches, settings, showBranchesPopup));
      }

      DefaultActionGroup branchesGroup = new DefaultActionGroup("Branches", branchActionGroups);
      branchesGroup.setPopup(showBranchesPopup);
      groups.add(branchesGroup);
    }

    if (!tagRefs.isEmpty()) {
      boolean showTagsPopup = tagRefs.size() > MAX_TAG_GROUPS;

      List<AnAction> tagActionGroups = new ArrayList<>();
      for (VcsRef ref : tagRefs) {
        tagActionGroups.add(createTagGroup(project, ref, root, showTagsPopup));
      }

      DefaultActionGroup tagsGroup = new DefaultActionGroup("Tags", tagActionGroups);
      tagsGroup.setPopup(showTagsPopup);
      groups.add(tagsGroup);
    }

    return groups.toArray(AnAction.EMPTY_ARRAY);
  }

  @NotNull
  private static AnAction createBranchGroup(@NotNull Project project,
                                            @NotNull VcsRef ref,
                                            @NotNull GitRepository repository,
                                            @NotNull List<? extends GitRepository> allRepositories,
                                            @NotNull Set<String> commonBranches,
                                            @NotNull GitVcsSettings settings,
                                            boolean showBranchesPopup) {
    boolean isSyncBranch = settings.getSyncSetting() != DvcsSyncSettings.Value.DONT_SYNC &&
                           allRepositories.size() > 1 && commonBranches.contains(ref.getName());
    boolean isLocal = ref.getType() == GitRefManager.LOCAL_BRANCH;

    List<AnAction> actions = new ArrayList<>(3);

    if (isSyncBranch) {
      ActionGroup allReposActions = createBranchActions(project, allRepositories, ref, repository, isLocal);
      allReposActions.getTemplatePresentation().setText("In All Repositories");
      allReposActions.setPopup(true);
      actions.add(allReposActions);
      actions.add(Separator.getInstance());
    }

    ActionGroup singleRepoActions = createBranchActions(project, Collections.singletonList(repository), ref, repository, isLocal);
    singleRepoActions.setPopup(false);
    actions.add(singleRepoActions);

    String text = showBranchesPopup ? ref.getName() : "Branch '" + ref.getName() + "'";
    ActionGroup group = new DefaultActionGroup(actions);
    group.getTemplatePresentation().setText(text, false);
    group.setPopup(true);
    return group;
  }

  @NotNull
  private static AnAction createTagGroup(@NotNull Project project,
                                         @NotNull VcsRef ref,
                                         @NotNull GitRepository repository,
                                         boolean showTagsPopup) {
    ActionGroup singleRepoActions = createTagActions(project, Collections.singletonList(repository), ref, repository);
    singleRepoActions.setPopup(false);

    String text = showTagsPopup ? ref.getName() : "Tag '" + ref.getName() + "'";
    ActionGroup group = new DefaultActionGroup(singleRepoActions);
    group.getTemplatePresentation().setText(text, false);
    group.setPopup(true);
    return group;
  }

  @NotNull
  private static ActionGroup createBranchActions(@NotNull Project project,
                                                 @NotNull List<? extends GitRepository> repositories,
                                                 @NotNull VcsRef ref,
                                                 @NotNull GitRepository selectedRepository,
                                                 boolean isLocal) {
    if (isLocal) {
      return new LocalBranchActions(project, repositories, ref.getName(), selectedRepository);
    }
    else {
      return new RemoteBranchActions(project, repositories, ref.getName(), selectedRepository);
    }
  }

  @NotNull
  private static ActionGroup createTagActions(@NotNull Project project,
                                              @NotNull List<? extends GitRepository> repositories,
                                              @NotNull VcsRef ref,
                                              @NotNull GitRepository selectedRepository) {
    return new GitBranchPopupActions.TagActions(project, repositories, ref.getName(), selectedRepository);
  }
}
