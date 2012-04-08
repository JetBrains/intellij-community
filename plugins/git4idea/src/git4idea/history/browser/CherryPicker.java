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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import git4idea.PlatformFacade;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT;
import static git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK;

public class CherryPicker {

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final PlatformFacade myPlatformFacade;
  @NotNull private final ChangeListManager myChangeListManager;
  private final boolean myAutoCommit;

  public CherryPicker(@NotNull Project project, @NotNull Git git, @NotNull PlatformFacade platformFacade, boolean autoCommit) {
    myProject = project;
    myGit = git;
    myPlatformFacade = platformFacade;
    myAutoCommit = autoCommit;
    myChangeListManager = myPlatformFacade.getChangeListManager(myProject);
  }

  public void cherryPick(@NotNull Map<GitRepository, List<GitCommit>> commitsInRoots) {
    List<GitCommit> successfulCommits = new ArrayList<GitCommit>();
    for (Map.Entry<GitRepository, List<GitCommit>> entry : commitsInRoots.entrySet()) {
      if (!cherryPick(entry.getKey(), entry.getValue(), successfulCommits)) {
        return;
      }
    }
    notifySuccess(successfulCommits);
  }

  private boolean cherryPick(@NotNull GitRepository repository, @NotNull List<GitCommit> commits,
                             @NotNull List<GitCommit> successfulCommits) {
    if (myAutoCommit) {
      GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(CHERRY_PICK_CONFLICT);
      GitSimpleEventDetector localChangesOverwrittenDetector = new GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK);
      for (GitCommit commit : commits) {
        GitCommandResult result = myGit.cherryPick(repository, commit.getHash().getValue(), true,
                                                   conflictDetector, localChangesOverwrittenDetector);
        if (result.success()) {
          successfulCommits.add(commit);
        }
        else if (conflictDetector.hasHappened()) {
          boolean mergeCompleted = new CherryPickConflictResolver(myProject, myGit, myPlatformFacade, repository.getRoot(),
                                                                  commit.getShortHash().getString(), commit.getAuthor(),
                                                                  commit.getSubject()).merge();
          if (mergeCompleted) {
            boolean committed = updateChangeListManagerAndShowCommitDialogIfNeeded(commit, true);
            if (!committed) {
              notifyConflictWarning(commit, successfulCommits);
              return false;
            }
            else {
              successfulCommits.add(commit);
            }
          }
          else {
            updateChangeListManagerAndShowCommitDialogIfNeeded(commit, false);
            notifyConflictWarning(commit, successfulCommits);
            return false;
          }
        }
        else if (localChangesOverwrittenDetector.hasHappened()) {
          notifyError("Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed.",
                      commit, successfulCommits);
          return false;
        }
        else {
          notifyError(result.getErrorOutputAsHtmlString(), commit, successfulCommits);
          return false;
        }
      }
    }
    return true;
  }

  private void notifyConflictWarning(GitCommit commit, List<GitCommit> successfulCommits) {
    String description = commitDetails(commit);
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifyWeakWarning("Cherry-picked with conflicts", description);
  }

  private boolean updateChangeListManagerAndShowCommitDialogIfNeeded(@NotNull final GitCommit commit, boolean showCommitDialog) {
    final Collection<FilePath> paths = ChangesUtil.getPaths(commit.getChanges());
    refreshChangedFiles(paths);
    final String commitMessage = createCommitMessage(commit, paths);
    LocalChangeList changeList = createChangeListAfterUpdate(commit.getChanges(), paths, commitMessage);
    if (showCommitDialog) {
      return showCommitDialog(commit, changeList, commitMessage);
    }
    return false;
  }

  @NotNull
  private LocalChangeList createChangeListAfterUpdate(@NotNull final List<Change> changes, @NotNull final Collection<FilePath> paths,
                                                      @NotNull final String commitMessage) {
    final AtomicReference<LocalChangeList> changeList = new AtomicReference<LocalChangeList>();
    myChangeListManager.invokeAfterUpdate(new Runnable() {
      public void run() {
        changeList.set(createChangeList(changes, commitMessage));
      }
    }, InvokeAfterUpdateMode.SILENT, "", new Consumer<VcsDirtyScopeManager>() {
      public void consume(VcsDirtyScopeManager vcsDirtyScopeManager) {
        vcsDirtyScopeManager.filePathsDirty(paths, null);
      }
    }, ModalityState.NON_MODAL);

    return changeList.get();
  }

  @NotNull
  private String createCommitMessage(@NotNull GitCommit commit, @NotNull Collection<FilePath> paths) {
    CheckinEnvironment ce = myPlatformFacade.getVcs(myProject).getCheckinEnvironment();
    String message = ce == null ? null : ce.getDefaultMessageFor(ArrayUtil.toObjectArray(paths, FilePath.class));
    message = message == null ? commit.getDescription() + "\n(cherry-picked from " + commit.getShortHash().getString() + ")" : message;
    return message;
  }

  private boolean showCommitDialog(@NotNull GitCommit commit, @NotNull LocalChangeList changeList, @NotNull String commitMessage) {
    return myPlatformFacade.getVcsHelper(myProject).commitChanges(commit.getChanges(), changeList, commitMessage);
  }

  private void notifyError(@NotNull String content, @NotNull GitCommit failedCommit, @NotNull List<GitCommit> successfulCommits) {
    String description = "Cherry-pick failed for " + commitDetails(failedCommit) + "<br/>" + content;
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifyError("Cherry-pick failed", description);
  }

  @NotNull
  private static String getSuccessfulCommitDetailsIfAny(@NotNull List<GitCommit> successfulCommits) {
    String description = "";
    if (!successfulCommits.isEmpty()) {
      description += "<br/>However it succeeded for the following " + StringUtil.pluralize("commit", successfulCommits.size()) + ": <br/>";
      description = getCommitsDetails(successfulCommits);
    }
    return description;
  }

  private void notifySuccess(@NotNull List<GitCommit> successfulCommits) {
    String description = getCommitsDetails(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifySuccess("Cherry-pick successful", description);
  }

  @NotNull
  private static String getCommitsDetails(@NotNull List<GitCommit> successfulCommits) {
    String description = "";
    for (GitCommit commit : successfulCommits) {
      description += commitDetails(commit);
    }
    return description;
  }

  @NotNull
  private static String commitDetails(@NotNull GitCommit commit) {
    return commit.getShortHash().toString() + " \"" + commit.getSubject() + "\"";
  }

  private void refreshChangedFiles(@NotNull Collection<FilePath> filePaths) {
    for (FilePath file : filePaths) {
      VirtualFile vf = myPlatformFacade.getLocalFileSystem().refreshAndFindFileByPath(file.getPath());
      if (vf != null) {
        vf.refresh(false, false);
      }
    }
  }

  @NotNull
  private LocalChangeList createChangeList(@NotNull List<Change> changes, @NotNull String commitMessage) {
    if (!changes.isEmpty()) {
      final LocalChangeList changeList = myChangeListManager.addChangeList(commitMessage, null);
      myChangeListManager.moveChangesTo(changeList, changes.toArray(new Change[changes.size()]));
      myChangeListManager.setDefaultChangeList(changeList);
      return changeList;
    }
    return myChangeListManager.getDefaultChangeList();
  }

  private static class CherryPickConflictResolver extends GitConflictResolver {

    @NotNull private final VirtualFile myRoot;
    @NotNull private final String myCommitHash;
    @NotNull private final String myCommitAuthor;
    @NotNull private final String myCommitMessage;
    @NotNull private final Git myGit;
    @NotNull private final PlatformFacade myPlatformFacade;

    public CherryPickConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull PlatformFacade facade, @NotNull VirtualFile root,
                                      @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {
      super(project, git, facade, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
      myGit = git;
      myPlatformFacade = facade;
      myRoot = root;
      myCommitHash = commitHash;
      myCommitAuthor = commitAuthor;
      myCommitMessage = commitMessage;
    }

    private static Params makeParams(String commitHash, String commitAuthor, String commitMessage) {
      Params params = new Params();
      params.setErrorNotificationTitle("Cherry-picked with conflicts");
      params.setMergeDialogCustomizer(new CherryPickMergeDialogCustomizer(commitHash, commitAuthor, commitMessage));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      myPlatformFacade.getNotificator(myProject).notifyStrongWarning("Conflicts were not resolved during cherry-pick",
                                                                     "Cherry-pick is not complete, you have unresolved merges in your working tree<br/>" +
                                                                     "<a href='resolve'>Resolve</a> conflicts.",
                                                                     new NotificationListener() {
                                                                       @Override
                                                                       public void hyperlinkUpdate(@NotNull Notification notification,
                                                                                                   @NotNull HyperlinkEvent event) {
                                                                         if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                                           if (event.getDescription().equals("resolve")) {
                                                                             new CherryPickConflictResolver(myProject, myGit,
                                                                                                            myPlatformFacade, myRoot,
                                                                                                            myCommitHash, myCommitAuthor,
                                                                                                            myCommitMessage)
                                                                               .mergeNoProceed();
                                                                           }
                                                                         }
                                                                       }
                                                                     });
    }
  }

  private static class CherryPickMergeDialogCustomizer extends MergeDialogCustomizer {

    private String myCommitHash;
    private String myCommitAuthor;
    private String myCommitMessage;

    public CherryPickMergeDialogCustomizer(String commitHash, String commitAuthor, String commitMessage) {
      myCommitHash = commitHash;
      myCommitAuthor = commitAuthor;
      myCommitMessage = commitMessage;
    }

    @Override
    public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
      return "<html>Conflicts during cherry-picking commit <code>" + myCommitHash + "</code> made by " + myCommitAuthor + "<br/>" +
             "<code>\"" + myCommitMessage + "\"</code></html>";
    }

    @Override
    public String getLeftPanelTitle(VirtualFile file) {
      return "Local changes";
    }

    @Override
    public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
      return "<html>Changes from cherry-pick <code>" + myCommitHash + "</code>";
    }
  }

}
