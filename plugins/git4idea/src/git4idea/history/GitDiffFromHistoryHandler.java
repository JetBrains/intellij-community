/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowser;
import com.intellij.openapi.vcs.history.CurrentRevision;
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link DiffFromHistoryHandler#showDiff(FilePath, VcsFileRevision, VcsFileRevision) "Show Diff" for 2 revision} calls the common code.
 * {@link DiffFromHistoryHandler#showDiff(AnActionEvent, FilePath, VcsFileRevision) "Show diff" for 1 revision}
 * behaves differently for merge commits: for them it shown a popup displaying the parents of the selected commit. Selecting a parent
 * from the popup shows the difference with this parent.
 * If an ordinary (not merge) revision with 1 parent, it is the same as usual: just compare with the parent;
 *
 * @author Kirill Likhodedov
 */
public class GitDiffFromHistoryHandler implements DiffFromHistoryHandler {
  
  private static final Logger LOG = Logger.getInstance(GitDiffFromHistoryHandler.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  public GitDiffFromHistoryHandler(@NotNull Project project) {
    myProject = project;
    myGit = ServiceManager.getService(project, Git.class);
    myRepositoryManager = GitUtil.getRepositoryManager(project);
  }

  @Override
  public void showDiff(@NotNull AnActionEvent e, @NotNull FilePath filePath, @NotNull VcsFileRevision revision) {
    GitFileRevision rev = (GitFileRevision)revision;
    Collection<String> parents = rev.getParents();
    if (parents.size() < 2) {
      showDiffWithParent(revision, filePath, parents);
    }
    else { // merge 
      showDiffForMergeCommit(e, filePath, rev, parents);
    }
  }

  @Override
  public void showDiff(@NotNull FilePath filePath, @NotNull VcsFileRevision revision1, @NotNull VcsFileRevision revision2) {
    doShowDiff(filePath, revision1, revision2, true);
  }

  private void doShowDiff(@NotNull FilePath filePath, @NotNull VcsFileRevision revision1, @NotNull VcsFileRevision revision2,
                          boolean autoSort) {
    if (!filePath.isDirectory()) {
      VcsHistoryUtil.showDifferencesInBackground(myProject, filePath, revision1, revision2, autoSort);
    }
    else if (revision2 instanceof CurrentRevision) {
      GitFileRevision left = (GitFileRevision)revision1;
      showDiffForDirectory(filePath, left.getHash(), null);
    }
    else {
      GitFileRevision left = (GitFileRevision)revision1;
      GitFileRevision right = (GitFileRevision)revision2;
      if (autoSort) {
        Pair<VcsFileRevision, VcsFileRevision> pair = VcsHistoryUtil.sortRevisions(revision1, revision2);
        left = (GitFileRevision)pair.first;
        right = (GitFileRevision)pair.second;
      }
      showDiffForDirectory(filePath, left.getHash(), right.getHash());
    }
  }

  private void showDiffForDirectory(@NotNull final FilePath path, @NotNull final String hash1, @Nullable final String hash2) {
    GitRepository repository = getRepository(path);
    calculateDiffInBackground(repository, path, hash1, hash2, new Consumer<List<Change>>() {
      @Override
      public void consume(List<Change> changes) {
        showDirDiffDialog(path, hash1, hash2, changes);
      }
    });
  }

  @NotNull
  private GitRepository getRepository(@NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    LOG.assertTrue(file != null, "VirtualFile can't be null for " + path); // we clicked on a file and asked its history => VF must exist.
    GitRepository repository = myRepositoryManager.getRepositoryForFile(file);
    LOG.assertTrue(repository != null, "Repository is null for " + file);
    return repository;
  }

  private void calculateDiffInBackground(@NotNull final GitRepository repository, @NotNull final FilePath path,
                                         @NotNull final String hash1, @Nullable final String hash2,
                                         final Consumer<List<Change>> successHandler) {
    new Task.Backgroundable(myProject, "Comparing revisions...") {
      private List<Change> myChanges;
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myChanges = new ArrayList<Change>(GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), hash1, hash2,
                                                                   Collections.singletonList(path)));
        }
        catch (VcsException e) {
          showError(e, "Error during requesting diff for directory");
        }
      }

      @Override
      public void onSuccess() {
        successHandler.consume(myChanges);
      }
    }.queue();
  }

  private void showDirDiffDialog(@NotNull FilePath path, @NotNull String hash1, @Nullable String hash2, @NotNull List<Change> diff) {
    DialogBuilder dialogBuilder = new DialogBuilder(myProject);
    String title;
    if (hash2 != null) {
      title = String.format("Difference between %s and %s in %s", GitUtil.getShortHash(hash1), GitUtil.getShortHash(hash2), path.getName());
    }
    else {
      title = String.format("Difference between %s and local version in %s", GitUtil.getShortHash(hash1), path.getName());
    }
    dialogBuilder.setTitle(title);
    dialogBuilder.setActionDescriptors(new DialogBuilder.ActionDescriptor[] { new DialogBuilder.CloseDialogAction()});
    final ChangesBrowser changesBrowser = new ChangesBrowser(myProject, null, diff, null, false, true,
                                                             null, ChangesBrowser.MyUseCase.COMMITTED_CHANGES, null);
    changesBrowser.setChangesToDisplay(diff);
    dialogBuilder.setCenterPanel(changesBrowser);
    dialogBuilder.show();
  }

  private void showDiffForMergeCommit(@NotNull final AnActionEvent event, @NotNull final FilePath filePath,
                                      @NotNull final GitFileRevision rev, @NotNull final Collection<String> parents) {

    final Consumer<Boolean> afterTouchCheck = new Consumer<Boolean>() {
      @Override
      public void consume(Boolean wasTouched) {
        if (wasTouched) {
          String message = filePath.getName() + " did not change in this merge commit";
          VcsBalloonProblemNotifier.showOverVersionControlView(GitDiffFromHistoryHandler.this.myProject, message, MessageType.INFO);
        }
        showPopup(event, rev, filePath, parents);
      }
    };

    if (filePath.isDirectory()) {        // for directories don't check if the file was modified in the merge commit
      afterTouchCheck.consume(false);
    }
    else {
      checkIfFileWasTouchedInBackground(filePath, rev, afterTouchCheck);
    }
  }

  private void checkIfFileWasTouchedInBackground(@NotNull final FilePath filePath, @NotNull final GitFileRevision rev,
                                                 @NotNull final Consumer<Boolean> afterTouchCheck) {
    new Task.Backgroundable(myProject, "Loading changes...", false) {
      private final AtomicBoolean fileTouched = new AtomicBoolean();

      @Override public void run(@NotNull ProgressIndicator indicator) {
        try {
          fileTouched.set(wasFileTouched(rev, filePath));
        }
        catch (VcsException e) {
          String logMessage = "Error happened while executing git show " + rev + ":" + filePath;
          showError(e, logMessage);
        }
      }

      @Override
      public void onSuccess() {
        afterTouchCheck.consume(fileTouched.get());
      }
    }.queue();
  }

  private void showError(VcsException e, String logMessage) {
    LOG.info(logMessage, e);
    VcsBalloonProblemNotifier.showOverVersionControlView(this.myProject, e.getMessage(), MessageType.ERROR);
  }

  private void showPopup(@NotNull AnActionEvent event, @NotNull GitFileRevision rev, @NotNull FilePath filePath,
                         @NotNull Collection<String> parents) {
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
  private ActionGroup createActionGroup(@NotNull GitFileRevision rev, @NotNull FilePath filePath, @NotNull Collection<String> parents) {
    Collection<AnAction> actions = new ArrayList<AnAction>(2);
    for (String parent : parents) {
      actions.add(createParentAction(rev, filePath, parent));
    }
    return new DefaultActionGroup(ArrayUtil.toObjectArray(actions, AnAction.class));
  }

  @NotNull
  private AnAction createParentAction(@NotNull GitFileRevision rev, @NotNull FilePath filePath, @NotNull String parent) {
    return new ShowDiffWithParentAction(filePath, rev, parent);
  }

  private void showDiffWithParent(@NotNull VcsFileRevision revision, @NotNull FilePath filePath, @NotNull Collection<String> parents) {
    VcsFileRevision parentRevision;
    if (parents.size() == 1) {
      String parent = parents.iterator().next();
      parentRevision = makeRevisionFromHash(filePath, parent);
    }
    else {
      parentRevision = VcsFileRevision.NULL;
    }
    doShowDiff(filePath, parentRevision, revision, false);
  }

  @NotNull
  private GitFileRevision makeRevisionFromHash(@NotNull FilePath filePath, @NotNull String hash) {
    return new GitFileRevision(myProject, filePath, new GitRevisionNumber(hash), false);
  }

  private boolean wasFileTouched(@NotNull GitFileRevision rev, @NotNull FilePath path) throws VcsException {
    GitRepository repository = getRepository(path);
    GitCommandResult result = myGit.show(repository, rev + ":" + path);
    if (result.success()) {
      return isFilePresentInOutput(repository, path, result.getOutput());
    }
    throw new VcsException(result.getErrorOutputAsJoinedString());
  }

  private static boolean isFilePresentInOutput(@NotNull GitRepository repository, @NotNull FilePath path, @NotNull List<String> output) {
    String relativePath = FileUtil.getRelativePath(repository.getRoot().getPath(), path.getPath(), '/');
    for (String line : output) {
      if (line.startsWith("---") || line.startsWith("+++")) {
        if (line.contains(relativePath)) {
          return true;
        }
      }
    }
    return false;
  }

  private class ShowDiffWithParentAction extends AnAction {

    @NotNull private final FilePath myFilePath;
    @NotNull private final GitFileRevision myRevision;
    @NotNull private final String myParentRevision;

    public ShowDiffWithParentAction(@NotNull FilePath filePath, @NotNull GitFileRevision rev, @NotNull String parent) {
      super(GitUtil.getShortHash(parent));
      myFilePath = filePath;
      myRevision = rev;
      myParentRevision = parent;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      doShowDiff(myFilePath, makeRevisionFromHash(myFilePath, myParentRevision), myRevision, false);
    }

  }
}
