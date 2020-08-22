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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import git4idea.GitLocalBranch;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase.getAffectedFilePaths;
import static com.intellij.vcsUtil.VcsFileUtilKt.isUnder;

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
 *   {@code 'git status --porcelain --untracked-files=no'} which gives a significant speed boost: the command gets more than twice
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
 */
public class GitUntrackedFilesHolder implements Disposable, AsyncVfsEventsListener {
  private static final Logger LOG = Logger.getInstance(GitUntrackedFilesHolder.class);

  private final Project myProject;
  private final VirtualFile myRoot;
  private final FilePath myRootPath;
  private final GitRepository myRepository;
  private final ChangeListManager myChangeListManager;
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final ProjectLevelVcsManager myVcsManager;
  private final GitRepositoryFiles myRepositoryFiles;
  private final Git myGit;

  private final Set<FilePath> myDefinitelyUntrackedFiles = new HashSet<>();
  private final Set<FilePath> myPossiblyUntrackedFiles = new HashSet<>();
  private boolean myReady;   // if false, total refresh is needed
  private final Object LOCK = new Object();
  private final Object RESCAN_LOCK = new Object();

  GitUntrackedFilesHolder(@NotNull GitRepository repository, @NotNull GitRepositoryFiles gitFiles) {
    myProject = repository.getProject();
    myRepository = repository;
    myRoot = repository.getRoot();
    myRootPath = VcsUtil.getFilePath(myRoot);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myGit = Git.getInstance();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);

    myRepositoryFiles = gitFiles;
  }

  void setupVfsListener(@NotNull Project project) {
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!project.isDisposed()) {
        AsyncVfsEventsPostProcessor.getInstance().addListener(this, this);
      }
    });
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myDefinitelyUntrackedFiles.clear();
      myPossiblyUntrackedFiles.clear();
    }
  }

  /**
   * Adds the file to the list of untracked.
   */
  public void add(@NotNull FilePath file) {
    synchronized (LOCK) {
      myDefinitelyUntrackedFiles.add(file);
      myPossiblyUntrackedFiles.add(file);
    }
  }

  /**
   * Adds several files to the list of untracked.
   */
  public void add(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      myDefinitelyUntrackedFiles.addAll(files);
      myPossiblyUntrackedFiles.addAll(files);
    }
  }

  /**
   * Removes several files from untracked.
   */
  public void remove(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      myDefinitelyUntrackedFiles.removeAll(files);
      myPossiblyUntrackedFiles.addAll(files);
    }
  }

  /**
   * Marks files as possibly untracked to be checked on the next {@link #retrieveUntrackedFilePaths} call.
   * @param files files that are possibly untracked.
   */
  public void markPossiblyUntracked(@NotNull Collection<? extends FilePath> files) {
    synchronized (LOCK) {
      myPossiblyUntrackedFiles.addAll(files);
    }
  }

  /**
   * Returns the list of unversioned files.
   * This method may be slow, if the full-refresh of untracked files is needed.
   * @return untracked files.
   * @throws VcsException if there is an unexpected error during Git execution.
   * @deprecated use {@link #retrieveUntrackedFilePaths} instead
   */
  @Deprecated
  @NotNull
  public Collection<VirtualFile> retrieveUntrackedFiles() throws VcsException {
   return ContainerUtil.mapNotNull(retrieveUntrackedFilePaths(), FilePath::getVirtualFile);
  }

  public void invalidate() {
    synchronized (LOCK) {
      myReady = false;
    }
  }

  @NotNull
  public Collection<FilePath> getUntrackedFilePaths() {
    synchronized (LOCK) {
      return new ArrayList<>(myDefinitelyUntrackedFiles);
    }
  }

  public boolean containsFile(@NotNull FilePath filePath) {
    synchronized (LOCK) {
      return myDefinitelyUntrackedFiles.contains(filePath);
    }
  }

  @NotNull
  public Collection<FilePath> retrieveUntrackedFilePaths() throws VcsException {
    synchronized (RESCAN_LOCK) {
      rescan();
    }

    return getUntrackedFilePaths();
  }

  /**
   * Queries Git to check the status of {@code myPossiblyUntrackedFiles} and moves them to {@code myDefinitelyUntrackedFiles}.
   */
  private void rescan() throws VcsException {
    @Nullable Set<FilePath> suspiciousFiles;
    synchronized (LOCK) {
      suspiciousFiles = myReady ? new HashSet<>(myPossiblyUntrackedFiles) : null;
      myPossiblyUntrackedFiles.clear();
    }

    Set<FilePath> untrackedFiles = myGit.untrackedFilePaths(myProject, myRoot, suspiciousFiles);

    untrackedFiles.removeIf(it -> {
      VirtualFile root = myVcsManager.getVcsRootFor(it);
      if (!myRoot.equals(root)) {
        LOG.warn(String.format("Ignoring untracked file under another root: %s; root: %s; mapped root: %s", it, myRoot, root));
        return true;
      }
      return false;
    });


    synchronized (LOCK) {
      if (suspiciousFiles != null) {
        // files that were suspicious (and thus passed to 'git ls-files'), but are not untracked, are definitely tracked.
        myDefinitelyUntrackedFiles.removeIf((definitelyUntrackedFile) -> isUnder(myRootPath, suspiciousFiles, definitelyUntrackedFile));
        myDefinitelyUntrackedFiles.addAll(untrackedFiles);
      }
      else {
        myDefinitelyUntrackedFiles.clear();
        myDefinitelyUntrackedFiles.addAll(untrackedFiles);
        myReady = true;
      }
    }
  }

  @Override
  public void filesChanged(@NotNull List<? extends VFileEvent> events) {
    boolean allChanged = false;
    Set<FilePath> filesToRefresh = new HashSet<>();

    for (VFileEvent event : events) {
      if (allChanged) {
        break;
      }
      String path = event.getPath();
      if (totalRefreshNeeded(myRepository, path)) {
        allChanged = true;
      }
      else {
        Set<FilePath> affectedPaths = getAffectedFilePaths(event);
        for (FilePath affectedFilePath : affectedPaths) {
          if (notIgnored(affectedFilePath)) {
            filesToRefresh.add(affectedFilePath);
          }
        }
      }
    }

    // if index has changed, no need to refresh specific files - we get the full status of all files
    if (allChanged) {
      rescanIgnoredFiles(() -> {
        LOG.debug(String.format("GitUntrackedFilesHolder: total refresh is needed, marking %s recursively dirty", myRoot));
        myDirtyScopeManager.dirDirtyRecursively(myRoot);
      });
      synchronized (LOCK) {
        myReady = false;
      }
    } else {
      synchronized (LOCK) {
        myPossiblyUntrackedFiles.addAll(filesToRefresh);
      }
    }
  }

  public static boolean totalRefreshNeeded(@NotNull GitRepository repository, @NotNull String path) {
    return indexChanged(repository, path) || externallyCommitted(repository, path) || headMoved(repository, path) ||
           headChanged(repository, path) || currentBranchChanged(repository, path) || gitignoreChanged(repository, path);
  }

  private static boolean headChanged(@NotNull GitRepository repository, @NotNull String path) {
    return repository.getRepositoryFiles().isHeadFile(path);
  }

  private static boolean currentBranchChanged(@NotNull GitRepository repository, @NotNull String path) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    return currentBranch != null && repository.getRepositoryFiles().isBranchFile(path, currentBranch.getFullName());
  }

  private static boolean headMoved(@NotNull GitRepository repository, @NotNull String path) {
    return repository.getRepositoryFiles().isOrigHeadFile(path);
  }

  public static boolean indexChanged(@NotNull GitRepository repository, @NotNull String path) {
    return repository.getRepositoryFiles().isIndexFile(path);
  }

  private static boolean externallyCommitted(@NotNull GitRepository repository, @NotNull String path) {
    return repository.getRepositoryFiles().isCommitMessageFile(path);
  }

  private static boolean gitignoreChanged(@NotNull GitRepository repository, @NotNull String path) {
    // TODO watch file stored in core.excludesfile
    return path.endsWith(GitRepositoryFiles.GITIGNORE) || repository.getRepositoryFiles().isExclude(path);
  }

  private void rescanIgnoredFiles(@NotNull Runnable doAfterRescan) { //TODO move to ignore manager
    myRepository.getIgnoredFilesHolder().startRescan(doAfterRescan);
  }

  private boolean notIgnored(@Nullable FilePath file) {
    return file != null && belongsToThisRepository(file) && !myChangeListManager.isIgnoredFile(file);
  }

  private boolean belongsToThisRepository(FilePath file) {
    return myRoot.equals(myVcsManager.getVcsRootFor(file));
  }
}
