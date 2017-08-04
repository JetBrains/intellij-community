/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBusConnection;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

/**
 * <p>
 *   Stores files which are untracked by the Git repository.
 *   Should be updated by calling {@link #add(VirtualFile)} and {@link #remove(Collection)}
 *   whenever the list of unversioned files changes.
 *   Able to get the list of unversioned files from Git.
 * </p>
 *
 * <p>
 *   This class is used by {@link git4idea.status.GitNewChangesCollector}.
 *   By keeping track of unversioned files in the Git repository we may invoke
 *   <code>'git status --porcelain --untracked-files=no'</code> which gives a significant speed boost: the command gets more than twice
 *   faster, because it doesn't need to seek for untracked files.
 * </p>
 *
 * <p>
 *   "Keeping track" means the following:
 *   <ul>
 *     <li>
 *       Once a file is created, it is added to untracked (by this class).
 *       Once a file is deleted, it is removed from untracked.
 *     </li>
 *     <li>
 *       Once a file is added to the index, it is removed from untracked.
 *       Once it is removed from the index, it is added to untracked.
 *     </li>
 *   </ul>
 * </p>
 * <p>
 *   In some cases (file creation/deletion) the file is not silently added/removed from the list - instead the file is marked as
 *   "possibly untracked" and Git is asked for the exact status of this file.
 *   It is needed, since the file may be created and added to the index independently, and events may race.
 * </p>
 * <p>
 *   Also, if .git/index changes, then a full refresh is initiated. The reason is not only untracked files tracking, but also handling
 *   committing outside IDEA, etc.
 * </p>
 * <p>
 *   Synchronization policy used in this class:<br/>
 *   myDefinitelyUntrackedFiles is accessed under the myDefinitelyUntrackedFiles lock.<br/>
 *   myPossiblyUntrackedFiles and myReady is accessed under the LOCK lock.<br/>
 *   This is done so, because the latter two variables are accessed from the AWT in after() and we don't want to lock the AWT long,
 *   while myDefinitelyUntrackedFiles is modified along with native request to Git.
 * </p>
 */
public class GitUntrackedFilesHolder implements Disposable, BulkFileListener {

  private static final Logger LOG = Logger.getInstance(GitUntrackedFilesHolder.class);

  private final Project myProject;
  private final VirtualFile myRoot;
  private final GitRepository myRepository;
  private final ChangeListManager myChangeListManager;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final ProjectLevelVcsManager myVcsManager;
  private final GitRepositoryFiles myRepositoryFiles;
  private final Git myGit;

  private final Set<VirtualFile> myDefinitelyUntrackedFiles = new HashSet<>();
  private final Set<VirtualFile> myPossiblyUntrackedFiles = new HashSet<>();
  private boolean myReady;   // if false, total refresh is needed
  private final Object LOCK = new Object();
  private final GitRepositoryManager myRepositoryManager;

  private final QueueProcessor<List<? extends VFileEvent>> myEventsProcessor;

  GitUntrackedFilesHolder(@NotNull GitRepository repository, @NotNull GitRepositoryFiles gitFiles) {
    myProject = repository.getProject();
    myRepository = repository;
    myRoot = repository.getRoot();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myGit = Git.getInstance();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);

    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myRepositoryFiles = gitFiles;

    myEventsProcessor = new QueueProcessor<>(events -> processEvents(events), myProject.getDisposed());
  }

  void setupVfsListener(@NotNull Project project) {
    if (!project.isDisposed()) {
      MessageBusConnection connection = project.getMessageBus().connect(this);
      connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }
  }

  @Override
  public void dispose() {
    synchronized (myDefinitelyUntrackedFiles) {
      myDefinitelyUntrackedFiles.clear();
    }
    synchronized (LOCK) {
      myPossiblyUntrackedFiles.clear();
    }
  }

  /**
   * Adds the file to the list of untracked.
   */
  public void add(@NotNull VirtualFile file) {
    synchronized (myDefinitelyUntrackedFiles) {
      myDefinitelyUntrackedFiles.add(file);
    }
  }

  /**
   * Adds several files to the list of untracked.
   */
  public void add(@NotNull Collection<VirtualFile> files) {
    synchronized (myDefinitelyUntrackedFiles) {
      myDefinitelyUntrackedFiles.addAll(files);
    }
  }

  /**
   * Removes several files from untracked.
   */
  public void remove(@NotNull Collection<VirtualFile> files) {
    synchronized (myDefinitelyUntrackedFiles) {
      myDefinitelyUntrackedFiles.removeAll(files);
    }
  }

  /**
   * Returns the list of unversioned files.
   * This method may be slow, if the full-refresh of untracked files is needed.
   * @return untracked files.
   * @throws VcsException if there is an unexpected error during Git execution.
   */
  @NotNull
  public Collection<VirtualFile> retrieveUntrackedFiles() throws VcsException {
    if (isReady()) {
      verifyPossiblyUntrackedFiles();
    } else {
      rescanAll();
    }
    synchronized (myDefinitelyUntrackedFiles) {
      return new ArrayList<>(myDefinitelyUntrackedFiles);
    }
  }

  public void invalidate() {
    synchronized (LOCK) {
      myReady = false;
    }
  }

  /**
   * Resets the list of untracked files after retrieving the full list of them from Git.
   */
  private void rescanAll() throws VcsException {
    Set<VirtualFile> untrackedFiles = myGit.untrackedFiles(myProject, myRoot, null);
    synchronized (myDefinitelyUntrackedFiles) {
      myDefinitelyUntrackedFiles.clear();
      myDefinitelyUntrackedFiles.addAll(untrackedFiles);
    }
    synchronized (LOCK) {
      myPossiblyUntrackedFiles.clear();
      myReady = true;
    }
  }

  /**
   * @return <code>true</code> if untracked files list is initialized and being kept up-to-date, <code>false</code> if full refresh is needed.
   */
  private boolean isReady() {
    synchronized (LOCK) {
      return myReady;
    }
  }

  /**
   * Queries Git to check the status of {@code myPossiblyUntrackedFiles} and moves them to {@code myDefinitelyUntrackedFiles}.
   */
  private void verifyPossiblyUntrackedFiles() throws VcsException {
    Set<VirtualFile> suspiciousFiles = new HashSet<>();
    synchronized (LOCK) {
      suspiciousFiles.addAll(myPossiblyUntrackedFiles);
      myPossiblyUntrackedFiles.clear();
    }

    synchronized (myDefinitelyUntrackedFiles) {
      Set<VirtualFile> untrackedFiles = myGit.untrackedFiles(myProject, myRoot, suspiciousFiles);
      suspiciousFiles.removeAll(untrackedFiles);
      // files that were suspicious (and thus passed to 'git ls-files'), but are not untracked, are definitely tracked.
      @SuppressWarnings("UnnecessaryLocalVariable")
      Set<VirtualFile> trackedFiles  = suspiciousFiles;

      myDefinitelyUntrackedFiles.addAll(untrackedFiles);
      myDefinitelyUntrackedFiles.removeAll(trackedFiles);
    }
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    myEventsProcessor.add(newArrayList(events));
  }

  private void processEvents(@NotNull List<? extends VFileEvent> events) {
    boolean allChanged = false;
    Set<VirtualFile> filesToRefresh = new HashSet<>();

    for (VFileEvent event : events) {
      if (allChanged) {
        break;
      }
      String path = event.getPath();
      if (totalRefreshNeeded(path)) {
        allChanged = true;
      }
      else {
        VirtualFile affectedFile = getAffectedFile(event);
        if (notIgnored(affectedFile)) {
          filesToRefresh.add(affectedFile);
        }
      }
    }

    // if index has changed, no need to refresh specific files - we get the full status of all files
    if (allChanged) {
      LOG.debug(String.format("GitUntrackedFilesHolder: total refresh is needed, marking %s recursively dirty", myRoot));
      myDirtyScopeManager.dirDirtyRecursively(myRoot);
      synchronized (LOCK) {
        myReady = false;
      }
    } else {
      synchronized (LOCK) {
        myPossiblyUntrackedFiles.addAll(filesToRefresh);
      }
    }
  }

  private boolean totalRefreshNeeded(@NotNull String path) {
    return indexChanged(path) || externallyCommitted(path) || headMoved(path) ||
           headChanged(path) || currentBranchChanged(path) || gitignoreChanged(path);
  }

  private boolean headChanged(@NotNull String path) {
    return myRepositoryFiles.isHeadFile(path);
  }

  private boolean currentBranchChanged(@NotNull String path) {
    GitLocalBranch currentBranch = myRepository.getCurrentBranch();
    return currentBranch != null && myRepositoryFiles.isBranchFile(path, currentBranch.getFullName());
  }

  private boolean headMoved(@NotNull String path) {
    return myRepositoryFiles.isOrigHeadFile(path);
  }

  private boolean indexChanged(@NotNull String path) {
    return myRepositoryFiles.isIndexFile(path);
  }

  private boolean externallyCommitted(@NotNull String path) {
    return myRepositoryFiles.isCommitMessageFile(path);
  }

  private boolean gitignoreChanged(@NotNull String path) {
    // TODO watch file stored in core.excludesfile
    return path.endsWith(".gitignore") || myRepositoryFiles.isExclude(path);
  }

  @Nullable
  private static VirtualFile getAffectedFile(@NotNull VFileEvent event) {
    if (event instanceof VFileCreateEvent || event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent || isRename(event)) {
      return event.getFile();
    } else if (event instanceof VFileCopyEvent) {
      VFileCopyEvent copyEvent = (VFileCopyEvent) event;
      return copyEvent.getNewParent().findChild(copyEvent.getNewChildName());
    }
    return null;
  }

  private static boolean isRename(@NotNull VFileEvent event) {
    return event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).getPropertyName().equals(VirtualFile.PROP_NAME);
  }

  private boolean notIgnored(@Nullable VirtualFile file) {
    return file != null && belongsToThisRepository(file) && !myChangeListManager.isIgnoredFile(file);
  }

  private boolean belongsToThisRepository(VirtualFile file) {
    // this check should be quick
    // we shouldn't create a full instance repository here because it may lead to SOE while many unversioned files will be processed
    GitRepository repository = myRepositoryManager.getRepositoryForRootQuick(myVcsManager.getVcsRootFor(file));
    return repository != null && repository.getRoot().equals(myRoot);
  }
}
