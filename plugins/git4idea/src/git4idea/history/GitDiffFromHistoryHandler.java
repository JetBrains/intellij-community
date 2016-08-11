/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.history;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.BaseDiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link DiffFromHistoryHandler#showDiffForTwo(com.intellij.openapi.project.Project, com.intellij.openapi.vcs.FilePath, com.intellij.openapi.vcs.history.VcsFileRevision, com.intellij.openapi.vcs.history.VcsFileRevision) "Show Diff" for 2 revision} calls the common code.
 * {@link DiffFromHistoryHandler#showDiffForOne(com.intellij.openapi.actionSystem.AnActionEvent, com.intellij.openapi.vcs.FilePath, com.intellij.openapi.vcs.history.VcsFileRevision, com.intellij.openapi.vcs.history.VcsFileRevision) "Show diff" for 1 revision}
 * behaves differently for merge commits: for them it shown a popup displaying the parents of the selected commit. Selecting a parent
 * from the popup shows the difference with this parent.
 * If an ordinary (not merge) revision with 1 parent, it is the same as usual: just compare with the parent;
 *
 * @author Kirill Likhodedov
 */
public class GitDiffFromHistoryHandler extends BaseDiffFromHistoryHandler<GitFileRevision> {

  private static final Logger LOG = Logger.getInstance(GitDiffFromHistoryHandler.class);

  @NotNull private final Git myGit;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  public GitDiffFromHistoryHandler(@NotNull Project project) {
    super(project);
    myGit = ServiceManager.getService(project, Git.class);
    myRepositoryManager = GitUtil.getRepositoryManager(project);
  }

  @Override
  public void showDiffForOne(@NotNull AnActionEvent e,
                             @NotNull Project project, @NotNull FilePath filePath,
                             @NotNull VcsFileRevision previousRevision,
                             @NotNull VcsFileRevision revision) {
    GitFileRevision rev = (GitFileRevision)revision;
    Collection<String> parents = rev.getParents();
    if (parents.size() < 2) {
      super.showDiffForOne(e, project, filePath, previousRevision, revision);
    }
    else { // merge
      showDiffForMergeCommit(e, filePath, rev, parents);
    }
  }

  @NotNull
  @Override
  protected List<Change> getChangesBetweenRevisions(@NotNull FilePath path, @NotNull GitFileRevision rev1, @Nullable GitFileRevision rev2)
    throws VcsException {
    GitRepository repository = getRepository(path);
    String hash1 = rev1.getHash();
    String hash2 = rev2 != null ? rev2.getHash() : null;

    return ContainerUtil
      .newArrayList(GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), hash1, hash2, Collections.singletonList(path)));
  }

  @NotNull
  @Override
  protected List<Change> getAffectedChanges(@NotNull FilePath path, @NotNull GitFileRevision rev) throws VcsException {
    GitRepository repository = getRepository(path);

    return ContainerUtil.newArrayList(
      GitChangeUtils.getRevisionChanges(repository.getProject(), repository.getRoot(), rev.getHash(), false, true, true).getChanges());
  }

  @NotNull
  @Override
  protected String getPresentableName(@NotNull GitFileRevision revision) {
    return DvcsUtil.getShortHash(revision.getHash());
  }

  @NotNull
  private GitRepository getRepository(@NotNull FilePath path) {
    GitRepository repository = myRepositoryManager.getRepositoryForFile(path);
    LOG.assertTrue(repository != null, "Repository is null for " + path);
    return repository;
  }

  private void showDiffForMergeCommit(@NotNull final AnActionEvent event, @NotNull final FilePath filePath,
                                      @NotNull final GitFileRevision rev, @NotNull final Collection<String> parents) {
    VcsHistorySession session = event.getData(VcsDataKeys.HISTORY_SESSION);
    List<VcsFileRevision> revisions = session != null ? session.getRevisionList() : null;
    checkIfFileWasTouchedAndFindParentsInBackground(filePath, rev, parents, revisions, new Consumer<MergeCommitPreCheckInfo>() {
      @Override
      public void consume(MergeCommitPreCheckInfo info) {
        if (!info.wasFileTouched()) {
          String message = String.format("There were no changes in %s in this merge commit, besides those which were made in both branches",
                                         filePath.getName());
          VcsBalloonProblemNotifier.showOverVersionControlView(GitDiffFromHistoryHandler.this.myProject, message, MessageType.INFO);
        }
        showPopup(event, rev, filePath, info.getParents());
      }
    });
  }

  private static class MergeCommitPreCheckInfo {
    private final boolean myWasFileTouched;
    private final Collection<GitFileRevision> myParents;

    private MergeCommitPreCheckInfo(boolean touched, Collection<GitFileRevision> parents) {
      myWasFileTouched = touched;
      myParents = parents;
    }

    public boolean wasFileTouched() {
      return myWasFileTouched;
    }

    public Collection<GitFileRevision> getParents() {
      return myParents;
    }
  }

  private void checkIfFileWasTouchedAndFindParentsInBackground(@NotNull final FilePath filePath,
                                                               @NotNull final GitFileRevision rev,
                                                               @NotNull final Collection<String> parentHashes,
                                                               @Nullable final List<VcsFileRevision> revisions,
                                                               @NotNull final Consumer<MergeCommitPreCheckInfo> resultHandler) {
    new Task.Backgroundable(myProject, "Loading changes...", true) {
      private MergeCommitPreCheckInfo myInfo;

      @Override public void run(@NotNull ProgressIndicator indicator) {
        try {
          GitRepository repository = getRepository(filePath);
          boolean fileTouched = wasFileTouched(repository, rev);
          Collection<GitFileRevision> parents = findParentRevisions(repository, rev, parentHashes, revisions);
          myInfo = new MergeCommitPreCheckInfo(fileTouched, parents);
        }
        catch (VcsException e) {
          String logMessage = "Error happened while executing git show " + rev + ":" + filePath;
          showError(e, logMessage);
        }
      }

      @Override
      public void onSuccess() {
        if (myInfo != null) { // if info == null => an exception happened
          resultHandler.consume(myInfo);
        }
      }
    }.queue();
  }

  @NotNull
  private Collection<GitFileRevision> findParentRevisions(@NotNull GitRepository repository,
                                                          @NotNull GitFileRevision currentRevision,
                                                          @NotNull Collection<String> parentHashes,
                                                          @Nullable List<VcsFileRevision> revisions) throws VcsException {
    // currentRevision is a merge revision.
    // the file could be renamed in one of the branches, i.e. the name in one of the parent revisions may be different from the name
    // in currentRevision. It can be different even in both parents, but it would a rename-rename conflict, and we don't handle such anyway.

    Collection<GitFileRevision> parents = new ArrayList<>(parentHashes.size());
    for (String parentHash : parentHashes) {
      parents.add(createParentRevision(repository, currentRevision, parentHash, revisions));
    }
    return parents;
  }

  @NotNull
  private GitFileRevision createParentRevision(@NotNull GitRepository repository,
                                               @NotNull GitFileRevision currentRevision,
                                               @NotNull String parentHash,
                                               @Nullable List<VcsFileRevision> revisions) throws VcsException {
    if (revisions != null) {
      for (VcsFileRevision revision : revisions) {
        if (((GitFileRevision)revision).getHash().equals(parentHash)) {
          return (GitFileRevision)revision;
        }
      }
    }

    FilePath currentRevisionPath = currentRevision.getPath();
    if (currentRevisionPath.isDirectory()) {
      // for directories the history doesn't follow renames
      return makeRevisionFromHash(currentRevisionPath, parentHash);
    }

    // can't limit by the path: in that case rename information will be missed
    Collection<Change> changes = GitChangeUtils.getDiff(myProject, repository.getRoot(), parentHash, currentRevision.getHash(), null);
    for (Change change : changes) {
      ContentRevision afterRevision = change.getAfterRevision();
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (afterRevision != null && afterRevision.getFile().equals(currentRevisionPath)) {
        // if the file was renamed, taking the path how it was in the parent; otherwise the path didn't change
        FilePath path = (beforeRevision != null ? beforeRevision.getFile() : afterRevision.getFile());
        return makeRevisionFromHash(path, parentHash);
      }
    }
    LOG.error(String.format("Could not find parent revision. Will use the path from parent revision. Current revision: %s, parent hash: %s",
                            currentRevision, parentHash));
    return makeRevisionFromHash(currentRevisionPath, parentHash);
  }

  private void showPopup(@NotNull AnActionEvent event, @NotNull GitFileRevision rev, @NotNull FilePath filePath,
                         @NotNull Collection<GitFileRevision> parents) {
    ActionGroup parentActions = createActionGroup(rev, filePath, parents);
    DataContext dataContext = SimpleDataContext.getProjectContext(myProject);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Choose parent to compare", parentActions, dataContext,
                                                                          JBPopupFactory.ActionSelectionAid.NUMBERING, true);
    showPopupInBestPosition(popup, event, dataContext);
  }

  private static void showPopupInBestPosition(@NotNull ListPopup popup, @NotNull AnActionEvent event, @NotNull DataContext dataContext) {
    if (event.getInputEvent() instanceof MouseEvent) {
      if (!event.getPlace().equals(ActionPlaces.UPDATE_POPUP)) {
        popup.show(new RelativePoint((MouseEvent)event.getInputEvent()));
      }
      else { // quick fix for invoking from the context menu: coordinates are calculated incorrectly there.
        popup.showInBestPositionFor(dataContext);
      }
    }
    else {
      popup.showInBestPositionFor(dataContext);
    }
  }

  @NotNull
  private ActionGroup createActionGroup(@NotNull GitFileRevision rev, @NotNull FilePath filePath, @NotNull Collection<GitFileRevision> parents) {
    Collection<AnAction> actions = new ArrayList<>(2);
    for (GitFileRevision parent : parents) {
      actions.add(createParentAction(rev, filePath, parent));
    }
    return new DefaultActionGroup(ArrayUtil.toObjectArray(actions, AnAction.class));
  }

  @NotNull
  private AnAction createParentAction(@NotNull GitFileRevision rev, @NotNull FilePath filePath, @NotNull GitFileRevision parent) {
    return new ShowDiffWithParentAction(filePath, rev, parent);
  }

  @NotNull
  private GitFileRevision makeRevisionFromHash(@NotNull FilePath filePath, @NotNull String hash) {
    return new GitFileRevision(myProject, filePath, new GitRevisionNumber(hash));
  }

  private boolean wasFileTouched(@NotNull GitRepository repository, @NotNull GitFileRevision rev) throws VcsException {
    GitCommandResult result = myGit.show(repository, rev.getHash());
    if (result.success()) {
      return isFilePresentInOutput(repository, rev.getPath(), result.getOutput());
    }
    throw new VcsException(result.getErrorOutputAsJoinedString());
  }

  private static boolean isFilePresentInOutput(@NotNull GitRepository repository, @NotNull FilePath path, @NotNull List<String> output) {
    String relativePath = getRelativePath(repository, path);
    for (String line : output) {
      if (line.startsWith("---") || line.startsWith("+++")) {
        if (line.contains(relativePath)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static String getRelativePath(@NotNull GitRepository repository, @NotNull FilePath path) {
    return FileUtil.getRelativePath(repository.getRoot().getPath(), path.getPath(), '/');
  }

  @NotNull
  private static String getRevisionDescription(@NotNull GitFileRevision parent) {
    String hash = DvcsUtil.getShortHash(parent.getHash());
    String message = parent.getCommitMessage();
    if (message != null) {
      int index = StringUtil.indexOfAny(message, "\n\r");
      if (index != -1) message = message.substring(0, index) + "...";
      if (message.length() > 40) message = message.substring(0, 35) + "...";
      return hash + " - " + message;
    }
    return hash;
  }

  private class ShowDiffWithParentAction extends DumbAwareAction {

    @NotNull private final FilePath myFilePath;
    @NotNull private final GitFileRevision myRevision;
    @NotNull private final GitFileRevision myParentRevision;

    public ShowDiffWithParentAction(@NotNull FilePath filePath, @NotNull GitFileRevision rev, @NotNull GitFileRevision parent) {
      super(getRevisionDescription(parent), parent.getCommitMessage(), null);
      myFilePath = filePath;
      myRevision = rev;
      myParentRevision = parent;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doShowDiff(myFilePath, myParentRevision, myRevision);
    }

  }
}
