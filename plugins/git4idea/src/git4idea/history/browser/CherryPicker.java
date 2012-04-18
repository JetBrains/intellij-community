/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;

public class CherryPicker {

  private static final Logger LOG = Logger.getInstance(CherryPicker.class);

  private final GitVcs myVcs;
  private final List<GitCommit> myCommits;
  @NotNull private final CheckinEnvironment myCheckinEnvironment;
  private final LowLevelAccess myAccess;

  private final List<VcsException> myExceptions;
  private final List<VcsException> myWarnings;
  private boolean myConflictsExist;
  private final ChangeListManager myChangeListManager;

  private final List<CherryPickedData> myCherryPickedData;

  public CherryPicker(GitVcs vcs, final List<GitCommit> commits, LowLevelAccess access) {
    myVcs = vcs;
    myCommits = commits;
    myAccess = access;

    myChangeListManager = PeriodicalTasksCloser.getInstance().safeGetComponent(myVcs.getProject(), ChangeListManager.class);
    CheckinEnvironment ce = myVcs.getCheckinEnvironment();
    LOG.assertTrue(ce != null);
    myCheckinEnvironment = ce;

    myExceptions = new ArrayList<VcsException>();
    myWarnings = new ArrayList<VcsException>();
    myCherryPickedData = new ArrayList<CherryPickedData>();
  }

  public void execute() {
    for (GitCommit commit : myCommits) {
      cherryPickStep(commit);
    }

    // remove those that are in newer lists
    checkListsForSamePaths();

    refreshChangedFiles();
    findAndProcessChangedForVcs();

    showResults();
  }

  private void refreshChangedFiles() {
    for (FilePath file : getAllChangedFiles()) {
      VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getPath());
      if (vf != null) {
        vf.refresh(false, false);
      }
    }
  }

  @NotNull
  private Collection<FilePath> getAllChangedFiles() {
    Collection<FilePath> files = new ArrayList<FilePath>();
    for (CherryPickedData data : myCherryPickedData) {
      files.addAll(data.getFiles());
    }
    return files;
  }

  private void findAndProcessChangedForVcs() {
    myChangeListManager.invokeAfterUpdate(new Runnable() {
      public void run() {
        moveToCorrectLists();
      }
    }, InvokeAfterUpdateMode.SILENT, "", new Consumer<VcsDirtyScopeManager>() {
      public void consume(VcsDirtyScopeManager vcsDirtyScopeManager) {
        vcsDirtyScopeManager.filePathsDirty(getAllChangedFiles(), null);
      }
    }, ModalityState.NON_MODAL);
  }

  private void showResults() {
    final Project project = myVcs.getProject();
    if (myExceptions.isEmpty() && !myConflictsExist) {
      showOverChangesView(project, "Successful cherry-pick into working tree, please commit changes", MessageType.INFO);
    } else {
      if (myExceptions.isEmpty()) {
        showOverChangesView(project, "Unresolved conflicts while cherry-picking. Resolve conflicts, then commit changes",
                            MessageType.WARNING);
      } else {
        showOverChangesView(project, "Errors in cherry-pick", MessageType.ERROR);
      }
    }
    if ((! myExceptions.isEmpty()) || (! myWarnings.isEmpty())) {
      myExceptions.addAll(myWarnings);
      AbstractVcsHelper.getInstance(project).showErrors(myExceptions, "Cherry-pick problems");
    }
  }

  private void moveToCorrectLists() {
    for (CherryPickedData pickedData : myCherryPickedData) {
      final Collection<FilePath> filePaths = pickedData.getFiles();
      final String message = pickedData.getCommitMessage();

      if (filePaths.isEmpty()) continue;

      final List<Change> changes = pathsToChanges(filePaths);
      pickedData.setChanges(changes);
      if (!changes.isEmpty()) {
        final LocalChangeList cl = myChangeListManager.addChangeList(message, null);
        pickedData.setChangeList(cl);
        myChangeListManager.moveChangesTo(cl, changes.toArray(new Change[changes.size()]));
      }
    }
  }

  @NotNull
  private List<Change> pathsToChanges(@NotNull Collection<FilePath> filePaths) {
    final List<Change> changes = new ArrayList<Change>(filePaths.size());
    for (FilePath filePath : filePaths) {
      changes.add(myChangeListManager.getChange(filePath));
    }
    return changes;
  }

  private void checkListsForSamePaths() {
    List<String> myMessagesInOrder = new ArrayList<String>(myCherryPickedData.size());
    Map<String, Collection<FilePath>> myFilesToMove = new HashMap<String, Collection<FilePath>>(myCherryPickedData.size());
    for (CherryPickedData data : myCherryPickedData) {
      myMessagesInOrder.add(data.getCommitMessage());
      myFilesToMove.put(data.getCommitMessage(), data.getFiles());
    }
    final GroupOfListsProcessor listsProcessor = new GroupOfListsProcessor();
    listsProcessor.process(myMessagesInOrder, myFilesToMove);
    final Set<String> lostSet = listsProcessor.getHaveLostSomething();
    markFilesMovesToNewerLists(myWarnings, lostSet, myFilesToMove);
  }

  private void cherryPickStep(@NotNull GitCommit commit) {
    try {
      if (!myAccess.cherryPick(commit)) {
        myConflictsExist = true;
      }
    }
    catch (VcsException e) {
      myExceptions.add(e);
    }
    final List<Change> changes = commit.getChanges();

    final Collection<FilePath> paths = ChangesUtil.getPaths(changes);
    String message = myCheckinEnvironment.getDefaultMessageFor(paths.toArray(new FilePath[paths.size()]));
    message = (message == null) ? commit.getDescription() + " (cherry picked from commit " + commit.getShortHash() + ")" : message;

    myCherryPickedData.add(new CherryPickedData(message, paths));
  }

  private static void markFilesMovesToNewerLists(List<VcsException> exceptions, Set<String> lostSet,
                                                 Map<String, Collection<FilePath>> filesToMove) {
    if (! lostSet.isEmpty()) {
      final StringBuilder sb = new StringBuilder("Some changes are moved from following list(s) to other:");
      boolean first = true;
      for (String s : lostSet) {
        if (filesToMove.get(s).isEmpty()) {
          final VcsException exc =
            new VcsException("Changelist not created since all files moved to other cherry-pick(s): '" + s + "'");
          exc.setIsWarning(true);
          exceptions.add(exc);
          continue;
        }
        sb.append(s);
        if (! first) {
          sb.append(", ");
        }
        first = false;
      }
      if (! first) {
        final VcsException exc = new VcsException(sb.toString());
        exc.setIsWarning(true);
        exceptions.add(exc);
      }
    }
  }

  private static class GroupOfListsProcessor {
    private final Set<String> myHaveLostSomething;

    private GroupOfListsProcessor() {
      myHaveLostSomething = new HashSet<String>();
    }

    public void process(final List<String> messagesInOrder, final Map<String, Collection<FilePath>> filesToMove) {
      // remove those that are in newer lists
      for (int i = 1; i < messagesInOrder.size(); i++) {
        final String message = messagesInOrder.get(i);
        final Collection<FilePath> currentFiles = filesToMove.get(message);

        for (int j = 0; j < i; j++) {
          final String previous = messagesInOrder.get(j);
          final boolean somethingChanged = filesToMove.get(previous).removeAll(currentFiles);
          if (somethingChanged) {
            myHaveLostSomething.add(previous);
          }
        }
      }
    }

    public Set<String> getHaveLostSomething() {
      return myHaveLostSomething;
    }
  }

  private static class CherryPickedData {

    private final String myCommitMessage;
    private final Collection<FilePath> myFiles;
    private LocalChangeList myChangeList;
    private Collection<Change> myChanges;

    private CherryPickedData(@NotNull String message, @NotNull Collection<FilePath> files) {
      myCommitMessage = message;
      myFiles = files;
    }

    public Collection<Change> getChanges() {
      return myChanges;
    }

    public LocalChangeList getChangeList() {
      return myChangeList;
    }

    public String getCommitMessage() {
      return myCommitMessage;
    }

    public Collection<FilePath> getFiles() {
      return myFiles;
    }

    public void setChanges(List<Change> changes) {
      myChanges = changes;
    }

    public void setChangeList(LocalChangeList changeList) {
      myChangeList = changeList;
    }
  }

}
