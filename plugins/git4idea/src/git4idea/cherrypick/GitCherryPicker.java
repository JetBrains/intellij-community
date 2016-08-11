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
package git4idea.cherrypick;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.cherrypick.VcsCherryPicker;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.util.VcsUserUtil;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.config.GitVcsSettings;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static git4idea.commands.GitSimpleEventDetector.Event.CHERRY_PICK_CONFLICT;
import static git4idea.commands.GitSimpleEventDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK;

public class GitCherryPicker extends VcsCherryPicker {

  private static final Logger LOG = Logger.getInstance(GitCherryPicker.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  public GitCherryPicker(@NotNull Project project, @NotNull Git git) {
    myProject = project;
    myGit = git;
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
  }

  public void cherryPick(@NotNull List<VcsFullCommitDetails> commits) {
    Map<GitRepository, List<VcsFullCommitDetails>> commitsInRoots = DvcsUtil.groupCommitsByRoots(myRepositoryManager, commits);
    LOG.info("Cherry-picking commits: " + toString(commitsInRoots));
    List<GitCommitWrapper> successfulCommits = ContainerUtil.newArrayList();
    List<GitCommitWrapper> alreadyPicked = ContainerUtil.newArrayList();
    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      for (Map.Entry<GitRepository, List<VcsFullCommitDetails>> entry : commitsInRoots.entrySet()) {
        GitRepository repository = entry.getKey();
        boolean result = cherryPick(repository, entry.getValue(), successfulCommits, alreadyPicked);
        repository.update();
        if (!result) {
          return;
        }
      }
      notifyResult(successfulCommits, alreadyPicked);
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(myProject, token);
    }
  }

  @NotNull
  private static String toString(@NotNull final Map<GitRepository, List<VcsFullCommitDetails>> commitsInRoots) {
    return StringUtil.join(commitsInRoots.keySet(), new Function<GitRepository, String>() {
      @Override
      public String fun(@NotNull GitRepository repository) {
        String commits = StringUtil.join(commitsInRoots.get(repository), new Function<VcsFullCommitDetails, String>() {
          @Override
          public String fun(VcsFullCommitDetails details) {
            return details.getId().asString();
          }
        }, ", ");
        return getShortRepositoryName(repository) + ": [" + commits + "]";
      }
    }, "; ");
  }

  // return true to continue with other roots, false to break execution
  private boolean cherryPick(@NotNull GitRepository repository, @NotNull List<VcsFullCommitDetails> commits,
                             @NotNull List<GitCommitWrapper> successfulCommits, @NotNull List<GitCommitWrapper> alreadyPicked) {
    for (VcsFullCommitDetails commit : commits) {
      GitSimpleEventDetector conflictDetector = new GitSimpleEventDetector(CHERRY_PICK_CONFLICT);
      GitSimpleEventDetector localChangesOverwrittenDetector = new GitSimpleEventDetector(LOCAL_CHANGES_OVERWRITTEN_BY_CHERRY_PICK);
      GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
        new GitUntrackedFilesOverwrittenByOperationDetector(repository.getRoot());
      boolean autoCommit = isAutoCommit();
      GitCommandResult result = myGit.cherryPick(repository, commit.getId().asString(), autoCommit,
                                                 conflictDetector, localChangesOverwrittenDetector, untrackedFilesDetector);
      GitCommitWrapper commitWrapper = new GitCommitWrapper(commit);
      if (result.success()) {
        if (autoCommit) {
          successfulCommits.add(commitWrapper);
        }
        else {
          boolean committed = updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository, commitWrapper,
                                                                                                  successfulCommits, alreadyPicked);
          if (!committed) {
            notifyCommitCancelled(commitWrapper, successfulCommits);
            return false;
          }
        }
      }
      else if (conflictDetector.hasHappened()) {
        boolean mergeCompleted = new CherryPickConflictResolver(myProject, myGit, repository.getRoot(),
                                                                commit.getId().asString(), VcsUserUtil.getShortPresentation(commit.getAuthor()),
                                                                commit.getSubject()).merge();

        if (mergeCompleted) {
          boolean committed = updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(repository, commitWrapper,
                                                                                                  successfulCommits, alreadyPicked);
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

        GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, repository.getRoot(),
                                                                  untrackedFilesDetector.getRelativeFilePaths(), "cherry-pick", description);
        return false;
      }
      else if (localChangesOverwrittenDetector.hasHappened()) {
        notifyError("Your local changes would be overwritten by cherry-pick.<br/>Commit your changes or stash them to proceed.",
                    commitWrapper, successfulCommits);
        return false;
      }
      else if (isNothingToCommitMessage(result)) {
        alreadyPicked.add(commitWrapper);
        return true;
      }
      else {
        notifyError(result.getErrorOutputAsHtmlString(), commitWrapper, successfulCommits);
        return false;
      }
    }
    return true;
  }

  private static boolean isNothingToCommitMessage(@NotNull GitCommandResult result) {
    if (!result.getErrorOutputAsJoinedString().isEmpty()) {
      return false;
    }
    String stdout = result.getOutputAsJoinedString();
    return stdout.contains("nothing to commit") || stdout.contains("previous cherry-pick is now empty");
  }

  private boolean updateChangeListManagerShowCommitDialogAndRemoveChangeListOnSuccess(@NotNull GitRepository repository,
                                                                                      @NotNull GitCommitWrapper commit,
                                                                                      @NotNull List<GitCommitWrapper> successfulCommits,
                                                                                      @NotNull List<GitCommitWrapper> alreadyPicked) {
    CherryPickData data = updateChangeListManager(commit.getCommit());
    if (data == null) {
      alreadyPicked.add(commit);
      return true;
    }
    boolean committed = showCommitDialogAndWaitForCommit(repository, commit, data.myChangeList, data.myCommitMessage);
    if (committed) {
      myChangeListManager.removeChangeList(data.myChangeList);
      successfulCommits.add(commit);
      return true;
    }
    return false;
  }

  private void notifyConflictWarning(@NotNull GitRepository repository, @NotNull GitCommitWrapper commit,
                                     @NotNull List<GitCommitWrapper> successfulCommits) {
    NotificationListener resolveLinkListener = new ResolveLinkListener(myProject, myGit, repository.getRoot(),
                                                                       commit.getCommit().getId().toShortString(),
                                                                       VcsUserUtil.getShortPresentation(commit.getCommit().getAuthor()),
                                                                       commit.getSubject());
    String description = commitDetails(commit)
                         + "<br/>Unresolved conflicts remain in the working tree. <a href='resolve'>Resolve them.<a/>";
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    VcsNotifier.getInstance(myProject).notifyImportantWarning("Cherry-picked with conflicts", description, resolveLinkListener);
  }

  private void notifyCommitCancelled(@NotNull GitCommitWrapper commit, @NotNull List<GitCommitWrapper> successfulCommits) {
    if (successfulCommits.isEmpty()) {
      // don't notify about cancelled commit. Notify just in the case when there were already successful commits in the queue.
      return;
    }
    String description = commitDetails(commit);
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    VcsNotifier.getInstance(myProject).notifyMinorWarning("Cherry-pick cancelled", description, null);
  }

  @Nullable
  private CherryPickData updateChangeListManager(@NotNull final VcsFullCommitDetails commit) {
    Collection<Change> changes = commit.getChanges();
    RefreshVFsSynchronously.updateChanges(changes);
    final String commitMessage = createCommitMessage(commit);
    final Collection<FilePath> paths = ChangesUtil.getPaths(changes);
    LocalChangeList changeList = createChangeListAfterUpdate(commit, paths, commitMessage);
    return changeList == null ? null : new CherryPickData(changeList, commitMessage);
  }

  @Nullable
  private LocalChangeList createChangeListAfterUpdate(@NotNull final VcsFullCommitDetails commit, @NotNull final Collection<FilePath> paths,
                                                      @NotNull final String commitMessage) {
    final CountDownLatch waiter = new CountDownLatch(1);
    final AtomicReference<LocalChangeList> changeList = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        myChangeListManager.invokeAfterUpdate(new Runnable() {
                                                public void run() {
                                                  changeList.set(createChangeListIfThereAreChanges(commit, commitMessage));
                                                  waiter.countDown();
                                                }
                                              }, InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED, "Cherry-pick",
                                              new Consumer<VcsDirtyScopeManager>() {
                                                public void consume(VcsDirtyScopeManager vcsDirtyScopeManager) {
                                                  vcsDirtyScopeManager.filePathsDirty(paths, null);
                                                }
                                              }, ModalityState.NON_MODAL);
      }
    }, ModalityState.NON_MODAL);
    try {
      boolean success = waiter.await(100, TimeUnit.SECONDS);
      if (!success) {
        LOG.error("Couldn't await for changelist manager refresh");
      }
    }
    catch (InterruptedException e) {
      LOG.error(e);
      return null;
    }

    return changeList.get();
  }

  @NotNull
  private static String createCommitMessage(@NotNull VcsFullCommitDetails commit) {
    return commit.getFullMessage() + "\n(cherry picked from commit " + commit.getId().toShortString() + ")";
  }

  private boolean showCommitDialogAndWaitForCommit(@NotNull final GitRepository repository, @NotNull final GitCommitWrapper commit,
                                                   @NotNull final LocalChangeList changeList, @NotNull final String commitMessage) {
    final AtomicBoolean commitSucceeded = new AtomicBoolean();
    final Semaphore sem = new Semaphore(0);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          cancelCherryPick(repository);
          Collection<Change> changes = commit.getCommit().getChanges();
          boolean commitNotCancelled = AbstractVcsHelper.getInstance(myProject).commitChanges(changes, changeList, commitMessage,
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
    if (isAutoCommit()) {
      removeCherryPickHead(repository);
    }
  }

  private void removeCherryPickHead(@NotNull GitRepository repository) {
    File cherryPickHeadFile = repository.getRepositoryFiles().getCherryPickHead();
    final VirtualFile cherryPickHead = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(cherryPickHeadFile);

    if (cherryPickHead != null && cherryPickHead.exists()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
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

  private void notifyError(@NotNull String content,
                           @NotNull GitCommitWrapper failedCommit,
                           @NotNull List<GitCommitWrapper> successfulCommits) {
    String description = commitDetails(failedCommit) + "<br/>" + content;
    description += getSuccessfulCommitDetailsIfAny(successfulCommits);
    VcsNotifier.getInstance(myProject).notifyError("Cherry-pick failed", description);
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

  private void notifyResult(@NotNull List<GitCommitWrapper> successfulCommits, @NotNull List<GitCommitWrapper> alreadyPicked) {
    if (alreadyPicked.isEmpty()) {
      VcsNotifier.getInstance(myProject).notifySuccess("Cherry-pick successful", getCommitsDetails(successfulCommits));
    }
    else if (!successfulCommits.isEmpty()) {
      String title = String.format("Cherry-picked %d commits from %d", successfulCommits.size(),
                                   successfulCommits.size() + alreadyPicked.size());
      String description = getCommitsDetails(successfulCommits) + "<hr/>" + formAlreadyPickedDescription(alreadyPicked, true);
      VcsNotifier.getInstance(myProject).notifySuccess(title, description);
    }
    else {
      VcsNotifier.getInstance(myProject).notifyImportantWarning("Nothing to cherry-pick",
                                                                formAlreadyPickedDescription(alreadyPicked, false));
    }
  }

  @NotNull
  private static String formAlreadyPickedDescription(@NotNull List<GitCommitWrapper> alreadyPicked, boolean but) {

    String hashes = StringUtil.join(alreadyPicked, new Function<GitCommitWrapper, String>() {
      @Override
      public String fun(GitCommitWrapper commit) {
        return commit.getCommit().getId().toShortString();
      }
    }, ", ");
    if (but) {
      String wasnt = alreadyPicked.size() == 1 ? "wasn't" : "weren't";
      String it = alreadyPicked.size() == 1 ? "it" : "them";
      return String.format("%s %s picked, because all changes from %s have already been applied.", hashes, wasnt, it);
    }
    return String.format("All changes from %s have already been applied", hashes);
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
    return commit.getCommit().getId().toShortString() + " " + commit.getOriginalSubject();
  }

  @Nullable
  private LocalChangeList createChangeListIfThereAreChanges(@NotNull VcsFullCommitDetails commit, @NotNull String commitMessage) {
    Collection<Change> originalChanges = commit.getChanges();
    if (originalChanges.isEmpty()) {
      LOG.info("Empty commit " + commit.getId());
      return null;
    }
    if (noChangesAfterCherryPick(originalChanges)) {
      LOG.info("No changes after cherry-picking " + commit.getId());
      return null;
    }

    String changeListName = createNameForChangeList(commitMessage, 0).replace('\n', ' ');
    LocalChangeList createdChangeList = ((ChangeListManagerEx)myChangeListManager).addChangeList(changeListName, commitMessage, commit);
    LocalChangeList actualChangeList = moveChanges(originalChanges, createdChangeList);
    if (actualChangeList != null && !actualChangeList.getChanges().isEmpty()) {
      return createdChangeList;
    }
    LOG.warn("No changes were moved to the changelist. Changes from commit: " + originalChanges +
             "\nAll changes: " + myChangeListManager.getAllChanges());
    myChangeListManager.removeChangeList(createdChangeList);
    return null;
  }

  private boolean noChangesAfterCherryPick(@NotNull Collection<Change> originalChanges) {
    final Collection<Change> allChanges = myChangeListManager.getAllChanges();
    return !ContainerUtil.exists(originalChanges, new Condition<Change>() {
      @Override
      public boolean value(Change change) {
        return allChanges.contains(change);
      }
    });
  }

  @Nullable
  private LocalChangeList moveChanges(@NotNull Collection<Change> originalChanges, @NotNull final LocalChangeList targetChangeList) {
    // 1. We have to listen to CLM changes, because moveChangesTo is asynchronous
    // 2. We have to collect the real target change list, because the original target list (passed to moveChangesTo) is not updated in time.
    final CountDownLatch moveChangesWaiter = new CountDownLatch(1);
    final AtomicReference<LocalChangeList> resultingChangeList = new AtomicReference<>();
    ChangeListAdapter listener = new ChangeListAdapter() {
      @Override
      public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
        if (toList instanceof LocalChangeList && targetChangeList.getId().equals(((LocalChangeList)toList).getId())) {
          resultingChangeList.set((LocalChangeList)toList);
          moveChangesWaiter.countDown();
        }
      }
    };
    try {
      myChangeListManager.addChangeListListener(listener);
      myChangeListManager.moveChangesTo(targetChangeList, originalChanges.toArray(new Change[originalChanges.size()]));
      boolean success = moveChangesWaiter.await(100, TimeUnit.SECONDS);
      if (!success) {
        LOG.error("Couldn't await for changes move.");
      }
      return resultingChangeList.get();
    }
    catch (InterruptedException e) {
      LOG.error(e);
      return null;
    }
    finally {
      myChangeListManager.removeChangeListListener(listener);
    }
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

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }

  @NotNull
  @Override
  public String getActionTitle() {
    return "Cherry-Pick";
  }

  private boolean isAutoCommit() {
    return GitVcsSettings.getInstance(myProject).isAutoCommitOnCherryPick();
  }

  @Override
  public boolean isEnabled(@NotNull VcsLog log, @NotNull Map<VirtualFile, List<Hash>> commits) {
    if (commits.isEmpty()) {
      return false;
    }

    for (VirtualFile root : commits.keySet()) {
      GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
      if (repository == null) {
        return false;
      }
      for (Hash commit : commits.get(root)) {
        GitLocalBranch currentBranch = repository.getCurrentBranch();
        Collection<String> containingBranches = log.getContainingBranches(commit, root);
        if (currentBranch != null && containingBranches != null && containingBranches.contains(currentBranch.getName())) {
          // already is contained in the current branch
          return false;
        }
      }
    }
    return true;
  }

  private static class CherryPickData {
    @NotNull private final LocalChangeList myChangeList;
    @NotNull private final String myCommitMessage;

    private CherryPickData(@NotNull LocalChangeList list, @NotNull String message) {
      myChangeList = list;
      myCommitMessage = message;
    }
  }

  private static class CherryPickConflictResolver extends GitConflictResolver {

    public CherryPickConflictResolver(@NotNull Project project,
                                      @NotNull Git git,
                                      @NotNull VirtualFile root,
                                      @NotNull String commitHash,
                                      @NotNull String commitAuthor,
                                      @NotNull String commitMessage) {
      super(project, git, Collections.singleton(root), makeParams(commitHash, commitAuthor, commitMessage));
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
    @NotNull private final VirtualFile myRoot;
    @NotNull private final String myHash;
    @NotNull private final String myAuthor;
    @NotNull private final String myMessage;

    public ResolveLinkListener(@NotNull Project project, @NotNull Git git, @NotNull VirtualFile root,
                               @NotNull String commitHash, @NotNull String commitAuthor, @NotNull String commitMessage) {

      myProject = project;
      myGit = git;
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
          new CherryPickConflictResolver(myProject, myGit, myRoot, myHash, myAuthor, myMessage).mergeNoProceed();
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
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return "<html>Conflicts during cherry-picking commit <code>" + myCommitHash + "</code> made by " + myCommitAuthor + "<br/>" +
             "<code>\"" + myCommitMessage + "\"</code></html>";
    }

    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return "Local changes";
    }

    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, VcsRevisionNumber revisionNumber) {
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
