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
package git4idea.cherrypick;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.GitPlatformFacade;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT;
import static git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK;

public class GitCherryPicker {

  /**
   * Name of the {@code .git/CHERRY_PICK_HEAD} file which is stored under {@code .git} when cherry-pick is in progress,
   * and contains the hash of the commit being cherry-picked.
   */
  private static final String CHERRY_PICK_HEAD_FILE = "CHERRY_PICK_HEAD";

  private static final Logger LOG = Logger.getInstance(GitCherryPicker.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitPlatformFacade myPlatformFacade;
  @NotNull private final ChangeListManager myChangeListManager;
  private final boolean myAutoCommit;

  public GitCherryPicker(@NotNull Project project, @NotNull Git git, @NotNull GitPlatformFacade platformFacade, boolean autoCommit) {
    myProject = project;
    myGit = git;
    myPlatformFacade = platformFacade;
    myAutoCommit = autoCommit;
    myChangeListManager = myPlatformFacade.getChangeListManager(myProject);
  }

  public void cherryPick(@NotNull Map<GitRepository, List<VcsFullCommitDetails>> commitsInRoots) {
    List<GitCommitWrapper> successfulCommits = new ArrayList<GitCommitWrapper>();
    for (Map.Entry<GitRepository, List<VcsFullCommitDetails>> entry : commitsInRoots.entrySet()) {
      if (!cherryPick(entry.getKey(), entry.getValue(), successfulCommits)) {
        return;
      }
    }
    notifySuccess(successfulCommits);
  }

  // return true to continue with other roots, false to break execution
  private boolean cherryPick(@NotNull GitRepository repository, @NotNull List<VcsFullCommitDetails> commits,
                             @NotNull List<GitCommitWrapper> successfulCommits) {
    for (VcsFullCommitDetails commit : commits) {
      GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(CHERRY_PICK_CONFLICT);
      GitSimpleEventDetector localChangesOverwrittenDetector = new GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK);
      GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
        new GitUntrackedFilesOverwrittenByOperationDetector(repository.getRoot());
      GitCommandResult result = myGit.cherryPick(repository, commit.getHash().asString(), myAutoCommit,
                                                 conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector);
      GitCommitWrapper commitWrapper = new GitCommitWrapper(commit);
      if (result.success()) {
        if (myAutoCommit) {
          successfulCommits.add(commitWrapper);
        }
        else {
          boolean committed = updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository, commitWrapper,
                                                                                                  successfulCommits);
          if (!committed) {
            notifyCommitCancelled(commitWrapper, successfulCommits);
            return false;
          }
        }
      }
      else if (conflictDetector.hasHappened()) {
        boolean mergeCompleted = new CherryPickConflictResolver(myProject, myGit, myPlatformFacade, repository.getRoot(),
                                                                commit.getHash().asString(), commit.getAuthor().getName(),
                                                                commit.getSubject()).merge();

        if (mergeCompleted) {
          boolean committed = updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository, commitWrapper,
                                                                                                  successfulCommits);
          if (!committed) {
            notifyCommitCancelled(commitWrapper, successfulCommits);
            return false;
          }
        }
        else {
          updateChangeListManager(commit);
          notifyConflictWarning(repository, commitWrapper, successfulCommits);
          return false;
        }
      }
      else if (untrackedFilesDetector.wasMessageDetected()) {
        String description = commitDetails(commitWrapper)
                             + "<br/>Some untracked working tree files would be overwritten by cherry-pick.<br/>" +
                             "Please move, remove or add them before you can cherry-pick. <a href='view'>View them</a>";
        description += getSuccessfulCommitDetailsIfAny(successfulCommits);

        UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(myProject, myPlatformFacade, untrackedFilesDetector.getFiles(),
                                                                 "cherry-pick", description);
        return false;
      }
      else if (localChangesOverwrittenDetector.hasHappened()) {
        notifyError("Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed.",
                    commitWrapper, successfulCommits);
        return false;
      }
      else {
        notifyError(result.getErrorOutputAsHtmlString(), commitWrapper, successfulCommits);
        return false;
      }
    }
    return true;
  }

  private boolean updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(@NotNull GitRepository repository,
                                                                                      @NotNull GitCommitWrapper commit,
                                                                                      @NotNull List<GitCommitWrapper> successfulCommits) {
    CherryPickData data = updateChangeListManager(commit.getCommit());
    boolean committed = showCommitDialogAndWaitForCommit(repository, commit, data.myChangeList, data.myCommitMessage);
    if (committed) {
      removeChangeList(data);
      successfulCommits.add(commit);
      return true;
    }
    return false;
  }

  private void removeChangeList(CherryPickData list) {
    myChangeListManager.setDefaultChangeList(list.myPreviouslyDefaultChangeList);
    if (!myChangeListManager.getDefaultChangeList().equals(list.myChangeList)) {
      myChangeListManager.removeChangeList(list.myChangeList);
    }
  }

  private void notifyConflictWarning(@NotNull GitRepository repository, @NotNull GitCommitWrapper commit,
                                     @NotNull List<GitCommitWrapper> successfulCommits) {
    NotificationListener resolveLinkListener = new ResolveLinkListener(myProject, myGit, myPlatformFacade, repository.getRoot(),
                                                                       commit.getCommit().getHash().toShortString(),
                                                                       commit.getCommit().getAuthor().getName(),
                                                                       commit.getSubject());
    String description = commitDetails(commit)
                         + "<br/>Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>";
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifyStrongWarning("Cherry-picked with conflicts", description, resolveLinkListener);
  }

  private void notifyCommitCancelled(@NotNull GitCommitWrapper commit, @NotNull List<GitCommitWrapper> successfulCommits) {
    if (successfulCommits.isEmpty()) {
      // don't notify about cancelled commit. Notify just in the case when there were already successful commits in the queue.
      return;
    }
    String description = commitDetails(commit);
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifyWeakWarning("Cherry-pick cancelled", description, null);
  }

  private CherryPickData updateChangeListManager(@NotNull final VcsFullCommitDetails commit) {
    final Collection<FilePath> paths = ChangesUtil.getPaths(commit.getChanges());
    refreshChangedFiles(paths);
    final String commitMessage = createCommitMessage(commit);
    LocalChangeList previouslyDefaultChangeList = myChangeListManager.getDefaultChangeList();
    LocalChangeList changeList = createChangeListAfterUpdate(commit, paths, commitMessage);
    return new CherryPickData(changeList, commitMessage, previouslyDefaultChangeList);
  }

  @NotNull
  private LocalChangeList createChangeListAfterUpdate(@NotNull final VcsFullCommitDetails commit, @NotNull final Collection<FilePath> paths,
                                                      @NotNull final String commitMessage) {
    final AtomicReference<LocalChangeList> changeList = new AtomicReference<LocalChangeList>();
    myPlatformFacade.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        myChangeListManager.invokeAfterUpdate(new Runnable() {
                                                public void run() {
                                                  changeList.set(createChangeList(commit, commitMessage));
                                                }
                                              }, InvokeAfterUpdateMode.SYNCHRONOUS_NOT_CANCELLABLE, "Cherry-pick",
                                              new Consumer<VcsDirtyScopeManager>() {
                                                public void consume(VcsDirtyScopeManager vcsDirtyScopeManager) {
                                                  vcsDirtyScopeManager.filePathsDirty(paths, null);
                                                }
                                              }, ModalityState.NON_MODAL
        );
      }
    }, ModalityState.NON_MODAL);


    return changeList.get();
  }

  @NotNull
  private static String createCommitMessage(@NotNull VcsFullCommitDetails commit) {
    return commit.getFullMessage() + "\n(cherry picked from commit " + commit.getHash().toShortString() + ")";
  }

  private boolean showCommitDialogAndWaitForCommit(@NotNull final GitRepository repository, @NotNull final GitCommitWrapper commit,
                                                   @NotNull final LocalChangeList changeList, @NotNull final String commitMessage) {
    final AtomicBoolean commitSucceeded = new AtomicBoolean();
    final Semaphore sem = new Semaphore(0);
    myPlatformFacade.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          cancelCherryPick(repository);
          Collection<Change> changes = commit.getCommit().getChanges();
          boolean commitNotCancelled = myPlatformFacade.getVcsHelper(myProject).commitChanges(changes, changeList, commitMessage,
                                                                                              new CommitResultHandler() {
            @Override
            public void onSuccess(@NotNull String commitMessage) {
              commit.setActualSubject(commitMessage);
              commitSucceeded.set(true);
              sem.release();
            }

            @Override
            public void onFailure() {
              commitSucceeded.set(false);
              sem.release();
            }
          });

          if (!commitNotCancelled) {
            commitSucceeded.set(false);
            sem.release();
          }
        } catch (Throwable t) {
          LOG.error(t);
          commitSucceeded.set(false);
          sem.release();
        }
      }
    }, ModalityState.NON_MODAL);

    // need additional waiting, because commitChanges is asynchronous
    try {
      sem.acquire();
    }
    catch (InterruptedException e) {
      LOG.error(e);
      return false;
    }
    return commitSucceeded.get();
  }

  /**
   * We control the cherry-pick workflow ourselves + we want to use partial commits ('git commit --only'), which is prohibited during
   * cherry-pick, i.e. until the CHERRY_PICK_HEAD exists.
   */
  private void cancelCherryPick(@NotNull GitRepository repository) {
    if (myAutoCommit) {
      removeCherryPickHead(repository);
    }
  }

  private void removeCherryPickHead(@NotNull GitRepository repository) {
    File cherryPickHeadFile = new File(repository.getGitDir().getPath(), CHERRY_PICK_HEAD_FILE);
    final VirtualFile cherryPickHead = myPlatformFacade.getLocalFileSystem().refreshAndFindFileByIoFile(cherryPickHeadFile);

    if (cherryPickHead != null && cherryPickHead.exists()) {
      myPlatformFacade.runWriteAction(new Runnable() {
        @Override
        public void run() {
          try {
            cherryPickHead.delete(this);
          }
          catch (IOException e) {
            // if CHERRY_PICK_HEAD is not deleted, the partial commit will fail, and the user will be notified anyway.
            // So here we just log the fact. It is happens relatively often, maybe some additional solution will follow.
            LOG.error(e);
          }
        }
      });
    }
    else {
      LOG.info("Cancel cherry-pick in " + repository.getPresentableUrl() + ": no CHERRY_PICK_HEAD found");
    }
  }

  private void notifyError(@NotNull String content, @NotNull GitCommitWrapper failedCommit, @NotNull List<GitCommitWrapper> successfulCommits) {
    String description = commitDetails(failedCommit) + "<br/>" + content;
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifyError("Cherry-pick failed", description);
  }

  @NotNull
  private static String getSuccessfulCommitDetailsIfAny(@NotNull List<GitCommitWrapper> successfulCommits) {
    String description = "";
    if (!successfulCommits.isEmpty()) {
      description += "<hr/>However cherry-pick succeeded for the following " + pluralize("commit", successfulCommits.size()) + ":<br/>";
      description += getCommitsDetails(successfulCommits);
    }
    return description;
  }

  private void notifySuccess(@NotNull List<GitCommitWrapper> successfulCommits) {
    String description = getCommitsDetails(successfulCommits);
    myPlatformFacade.getNotificator(myProject).notifySuccess("Cherry-pick successful", description);
  }

  @NotNull
  private static String getCommitsDetails(@NotNull List<GitCommitWrapper> successfulCommits) {
    String description = "";
    for (GitCommitWrapper commit : successfulCommits) {
      description += commitDetails(commit) + "<br/>";
    }
    return description.substring(0, description.length() - "<br/>".length());
  }

  @NotNull
  private static String commitDetails(@NotNull GitCommitWrapper commit) {
    return commit.getCommit().getHash().toShortString() + " " + commit.getOriginalSubject();
  }

  private void refreshChangedFiles(@NotNull Collection<FilePath> filePaths) {
    List<VirtualFile> virtualFiles = ContainerUtil.skipNulls(ContainerUtil.map(filePaths, new Function<FilePath, VirtualFile>() {
      @Override
      public VirtualFile fun(FilePath file) {
        return myPlatformFacade.getLocalFileSystem().refreshAndFindFileByPath(file.getPath());
      }
    }));
    VfsUtil.markDirtyAndRefresh(false, false, false, ArrayUtil.toObjectArray(virtualFiles, VirtualFile.class));
  }

  @NotNull
  private LocalChangeList createChangeList(@NotNull VcsFullCommitDetails commit, @NotNull String commitMessage) {
    Collection<Change> changes = commit.getChanges();
    if (!changes.isEmpty()) {
      String changeListName = createNameForChangeList(commitMessage, 0).replace('\n', ' ');
      final LocalChangeList changeList = ((ChangeListManagerEx)myChangeListManager).addChangeList(changeListName, commitMessage, commit);
      myChangeListManager.moveChangesTo(changeList, changes.toArray(new Change[changes.size()]));
      myChangeListManager.setDefaultChangeList(changeList);
      return changeList;
    }
    return myChangeListManager.getDefaultChangeList();
  }

  @NotNull
  private String createNameForChangeList(@NotNull String proposedName, int step) {
    for (LocalChangeList list : myChangeListManager.getChangeLists()) {
      if (list.getName().equals(nameWithStep(proposedName, step))) {
        return createNameForChangeList(proposedName, step + 1);
      }
    }
    return nameWithStep(proposedName, step);
  }

  private static String nameWithStep(String name, int step) {
    return step == 0 ? name : name + "-" + step;
  }

  private static class CherryPickData {
    private final LocalChangeList myChangeList;
    private final String myCommitMessage;
    private final LocalChangeList myPreviouslyDefaultChangeList;

    private CherryPickData(LocalChangeList list, String message, LocalChangeList previouslyDefaultChangeList) {
      myChangeList = list;
      myCommitMessage = message;
      myPreviouslyDefaultChangeList = previouslyDefaultChangeList;
    }
  }

  private static class CherryPickConflictResolver extends GitConflictResolver {

    public CherryPickConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull GitPlatformFacade facade, @NotNull VirtualFile root,
                                      @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {
      super(project, git, facade, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
    }

    private static Params makeParams(String commitHash, String commitAuthor, String commitMessage) {
      Params params = new Params();
      params.setErrorNotificationTitle("Cherry-picked with conflicts");
      params.setMergeDialogCustomizer(new CherryPickMergeDialogCustomizer(commitHash, commitAuthor, commitMessage));
      return params;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      // we show a [possibly] compound notification after cherry-picking all commits.
    }

  }

  private static class ResolveLinkListener implements NotificationListener {
    @NotNull private final Project myProject;
    @NotNull private final Git myGit;
    @NotNull private final GitPlatformFacade myFacade;
    @NotNull private final VirtualFile myRoot;
    @NotNull private final String myHash;
    @NotNull private final String myAuthor;
    @NotNull private final String myMessage;

    public ResolveLinkListener(@NotNull Project project, @NotNull Git git, @NotNull GitPlatformFacade facade, @NotNull VirtualFile root,
                               @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {

      myProject = project;
      myGit = git;
      myFacade = facade;
      myRoot = root;
      myHash = commitHash;
      myAuthor = commitAuthor;
      myMessage = commitMessage;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification,
                                @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if (event.getDescription().equals("resolve")) {
          new CherryPickConflictResolver(myProject, myGit, myFacade, myRoot, myHash, myAuthor, myMessage).mergeNoProceed();
        }
      }
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

  /**
   * This class is needed to hold both the original GitCommit, and the commit message which could be changed by the user.
   * Only the subject of the commit message is needed.
   */
  private static class GitCommitWrapper {
    @NotNull private final VcsFullCommitDetails myOriginalCommit;
    @NotNull private String myActualSubject;

    private GitCommitWrapper(@NotNull VcsFullCommitDetails commit) {
      myOriginalCommit = commit;
      myActualSubject = commit.getSubject();
    }

    @NotNull
    public String getSubject() {
      return myActualSubject;
    }

    public void setActualSubject(@NotNull String actualSubject) {
      myActualSubject = actualSubject;
    }

    @NotNull
    public VcsFullCommitDetails getCommit() {
      return myOriginalCommit;
    }

    public String getOriginalSubject() {
      return myOriginalCommit.getSubject();
    }
  }

}
