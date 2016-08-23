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
package git4idea.reset;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.*;
import git4idea.GitRemoteBranch;
import git4idea.config.GitSharedSettings;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static git4idea.GitUtil.HEAD;
import static git4idea.GitUtil.getRepositoryManager;
import static git4idea.reset.GitResetMode.MIXED;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class GitUncommitAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GitUncommitAction.class);
  private static final String FAILURE_TITLE = "Can't Undo Commit";
  private static final String COMMIT_NOT_IN_HEAD = "The commit is not in the current branch";
  private static final String COMMIT_PUSHED_TO_PROTECTED = "The commit is already pushed to protected branch ";

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    VcsLogData data = (VcsLogData)e.getData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER);
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (project == null || log == null || data == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    int selectedCommits = log.getSelectedShortDetails().size();
    if (selectedCommits != 1) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    VcsShortCommitDetails commit = log.getSelectedShortDetails().get(0);
    Hash hash = commit.getId();
    VirtualFile root = commit.getRoot();
    GitRepository repository = getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
    if (repository == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    // support undo only for the last commit in the branch
    DataPack dataPack = (DataPack)((VisiblePack)ui.getDataPack()).getDataPack();
    List<Integer> children = dataPack.getPermanentGraph().getChildren(data.getCommitIndex(hash, root));
    if (!children.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    // undoing merge commit is not allowed
    int parents = commit.getParents().size();
    if (parents != 1) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription("Selected commit has " + parents + " parents");
      return;
    }

    // allow reset only in current branch
    List<String> branches = data.getContainingBranchesGetter().getContainingBranchesFromCache(root, hash);
    if (branches != null) { // otherwise the information is not available yet, and we'll recheck harder in actionPerformed
      if (!branches.contains(HEAD)) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription(COMMIT_NOT_IN_HEAD);
        return;
      }

      // and not if pushed to a protected branch
      String protectedBranch = findProtectedRemoteBranch(repository, branches);
      if (protectedBranch != null) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription(COMMIT_PUSHED_TO_PROTECTED + protectedBranch);
        return;
      }
    }

    e.getPresentation().setEnabledAndVisible(true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLogData data = (VcsLogData)e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    VcsShortCommitDetails commit = assertNotNull(getFirstItem(log.getSelectedShortDetails()));
    VirtualFile root = commit.getRoot();
    Hash hash = commit.getId();
    GitRepository repository = assertNotNull(getRepositoryManager(project).getRepositoryForRoot(commit.getRoot()));

    List<String> branches = findContainingBranches(data, root, hash);

    if (!branches.contains(HEAD)) {
      Messages.showErrorDialog(project, COMMIT_NOT_IN_HEAD, FAILURE_TITLE);
      return;
    }

    // and not if pushed to a protected branch
    String protectedBranch = findProtectedRemoteBranch(repository, branches);
    if (protectedBranch != null) {
      Messages.showErrorDialog(project, COMMIT_PUSHED_TO_PROTECTED + protectedBranch, FAILURE_TITLE);
      return;
    }

    ChangeListChooser chooser = new ChangeListChooser(project, ChangeListManager.getInstance(project).getChangeListsCopy(),
                                                      null, "Select Target Changelist", commit.getSubject());
    chooser.show();
    LocalChangeList selectedList = chooser.getSelectedList();
    if (selectedList != null) {
      resetInBackground(data, repository, commit, selectedList);
    }
  }

  @NotNull
  private static List<String> findContainingBranches(@NotNull VcsLogData data, @NotNull VirtualFile root, @NotNull Hash hash) {
    ContainingBranchesGetter branchesGetter = data.getContainingBranchesGetter();
    List<String> branches = branchesGetter.getContainingBranchesFromCache(root, hash);
    if (branches != null) return branches;
    return branchesGetter.getContainingBranchesSynchronously(root, hash);
  }

  @Nullable
  private static String findProtectedRemoteBranch(@NotNull GitRepository repository, @NotNull List<String> branches) {
    GitSharedSettings settings = GitSharedSettings.getInstance(repository.getProject());
    // protected branches hold patterns for branch names without remote names
    return repository.getBranches().getRemoteBranches().stream().
             filter(it -> settings.isBranchProtected(it.getNameForRemoteOperations())).
             map(GitRemoteBranch::getNameForLocalOperations).
             filter(branches::contains).
             findAny().
             orElse(null);
  }

  private static void resetInBackground(@NotNull VcsLogData data,
                                        @NotNull GitRepository repository,
                                        @NotNull VcsShortCommitDetails commit,
                                        @NotNull LocalChangeList changeList) {
    Project project = repository.getProject();
    new Task.Backgroundable(project, "Undoing Last Commit...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Collection<Change> changesInCommit;
        try {
          changesInCommit = getChangesInCommit(data, commit);
        }
        catch (VcsException e) {
          String message = "Couldn't load changes of " + commit.getId().asString();
          LOG.warn(message, e);
          Notification notification = STANDARD_NOTIFICATION.createNotification("", message, NotificationType.ERROR, null);
          VcsNotifier.getInstance(project).notify(notification);
          return;
        }

        // TODO change notification title
        new GitResetOperation(project, singletonMap(repository, commit.getParents().get(0)), MIXED, indicator).execute();

        ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(project);
        changeListManager.invokeAfterUpdate(() -> {
          Collection<Change> changes = findLocalChangesFromCommit(changeListManager, changesInCommit);
          changeListManager.moveChangesTo(changeList, ArrayUtil.toObjectArray(changes, Change.class));
        }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "Refreshing Changes...", ModalityState.defaultModalityState());
      }
    }.queue();
  }

  @NotNull
  private static Collection<Change> getChangesInCommit(@NotNull VcsLogData data,
                                                       @NotNull VcsShortCommitDetails commit) throws VcsException {
    Hash hash = commit.getId();
    VirtualFile root = commit.getRoot();
    VcsFullCommitDetails details = getChangesFromCache(data, hash, root);
    if (details == null) {
      details = data.getLogProvider(root).readFullDetails(root, singletonList(hash.asString())).get(0);
    }
    return details.getChanges();
  }

  @Nullable
  private static VcsFullCommitDetails getChangesFromCache(@NotNull VcsLogData data, @NotNull Hash hash, @NotNull VirtualFile root) {
    Ref<VcsFullCommitDetails> details = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      details.set(data.getCommitDetailsGetter().getCommitDataIfAvailable(data.getCommitIndex(hash, root)));
    }, ModalityState.defaultModalityState());
    if (details.isNull() || details.get() instanceof LoadingDetails) return null;
    return details.get();
  }

  @NotNull
  private static Collection<Change> findLocalChangesFromCommit(@NotNull ChangeListManager changeListManager,
                                                               @NotNull Collection<Change> changesInCommit) {
    OpenTHashSet<Change> allChanges = new OpenTHashSet<>(changeListManager.getAllChanges());
    return ContainerUtil.map(changesInCommit, allChanges::get);
  }
}
