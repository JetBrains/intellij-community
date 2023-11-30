// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.CommonProcessors;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import git4idea.GitLocalBranch;
import git4idea.index.vfs.GitIndexFileSystemRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Listens to .git service files changes and updates {@link GitRepository} when needed.
 */
final class GitRepositoryUpdater implements Disposable, AsyncVfsEventsListener {
  private final @NotNull GitRepository myRepository;
  private final @NotNull Collection<VirtualFile> myRootDirs;
  private final @NotNull GitRepositoryFiles myRepositoryFiles;
  private final @Nullable VirtualFile myRemotesDir;
  private final @Nullable VirtualFile myHeadsDir;
  private final @Nullable VirtualFile myTagsDir;
  private final @NotNull Set<LocalFileSystem.WatchRequest> myWatchRequests;

  GitRepositoryUpdater(@NotNull GitRepository repository, @NotNull GitRepositoryFiles gitFiles) {
    myRepository = repository;
    myRootDirs = gitFiles.getRootDirs();
    myWatchRequests = LocalFileSystem.getInstance().addRootsToWatch(ContainerUtil.map(myRootDirs, VirtualFile::getPath), true);

    myRepositoryFiles = gitFiles;
    visitSubDirsInVfs();
    myHeadsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getRefsHeadsFile());
    myRemotesDir = VcsUtil.getVirtualFile(myRepositoryFiles.getRefsRemotesFile());
    myTagsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getRefsTagsFile());

    AsyncVfsEventsPostProcessor.getInstance().addListener(this, this);
  }

  @Override
  public void dispose() {
    LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
  }

  @Override
  public void filesChanged(@NotNull List<? extends @NotNull VFileEvent> events) {
    GitLocalBranch currentBranch = myRepository.getCurrentBranch();

    // which files in .git were changed
    boolean configChanged = false;
    boolean indexChanged = false;
    boolean headChanged = false;
    boolean headMoved = false;
    boolean branchFileChanged = false;
    boolean currentBranchChanged = false;
    boolean packedRefsChanged = false;
    boolean rebaseFileChanged = false;
    boolean mergeFileChanged = false;
    boolean externallyCommitted = false;
    boolean tagChanged = false;
    boolean gitignoreChanged = false;

    Set<VirtualFile> toReloadVfs = new HashSet<>();
    for (VFileEvent event : events) {
      String filePath = event.getPath();
      if (isRootDirChange(event)) {
        if (myRepositoryFiles.isConfigFile(filePath)) {
          configChanged = true;
        }
        else if (myRepositoryFiles.isIndexFile(filePath)) {
          indexChanged = true;
        }
        else if (myRepositoryFiles.isHeadFile(filePath)) {
          headChanged = true;
        }
        else if (myRepositoryFiles.isOrigHeadFile(filePath)) {
          headMoved = true;
        }
        else if (myRepositoryFiles.isBranchFile(filePath)) {
          // it is also possible, that a local branch with complex name ("folder/branch") was created => the folder also to be watched.
          branchFileChanged = true;
          ContainerUtil.addIfNotNull(toReloadVfs, myHeadsDir);

          if (currentBranch != null && myRepositoryFiles.isBranchFile(filePath, currentBranch.getFullName())) {
            currentBranchChanged = true;
          }
        }
        else if (myRepositoryFiles.isRemoteBranchFile(filePath)) {
          // it is possible, that a branch from a new remote was fetch => we need to add new remote folder to the VFS
          branchFileChanged = true;
          ContainerUtil.addIfNotNull(toReloadVfs, myRemotesDir);
        }
        else if (myRepositoryFiles.isPackedRefs(filePath)) {
          packedRefsChanged = true;
        }
        else if (myRepositoryFiles.isRebaseFile(filePath)) {
          rebaseFileChanged = true;
        }
        else if (myRepositoryFiles.isMergeFile(filePath)) {
          mergeFileChanged = true;
        }
        else if (myRepositoryFiles.isCommitMessageFile(filePath)) {
          externallyCommitted = true;
        }
        else if (myRepositoryFiles.isTagFile(filePath)) {
          tagChanged = true;
          ContainerUtil.addIfNotNull(toReloadVfs, myTagsDir);
        }
        else if (myRepositoryFiles.isExclude(filePath)) {
          // TODO watch file stored in core.excludesfile
          gitignoreChanged = true;
        }
      }
      else {
        if (filePath.endsWith(GitRepositoryFiles.GITIGNORE)) {
          gitignoreChanged = true;
        }
      }
    }
    for (VirtualFile dir : toReloadVfs) {
      VfsUtilCore.processFilesRecursively(dir, CommonProcessors.alwaysTrue());
    }

    if (headChanged || configChanged || branchFileChanged || packedRefsChanged || rebaseFileChanged || mergeFileChanged) {
      myRepository.update();
    }
    if (tagChanged || packedRefsChanged) {
      BackgroundTaskUtil.syncPublisher(myRepository.getProject(), GitRepository.GIT_REPO_CHANGE).repositoryChanged(myRepository);
    }
    if (configChanged) {
      BackgroundTaskUtil.syncPublisher(myRepository.getProject(), GitConfigListener.TOPIC).notifyConfigChanged(myRepository);
    }
    if (indexChanged || externallyCommitted || headMoved || headChanged || currentBranchChanged || gitignoreChanged) {
      VcsDirtyScopeManager.getInstance(myRepository.getProject()).dirDirtyRecursively(myRepository.getRoot());
      myRepository.getUntrackedFilesHolder().invalidate();
    }
    if (indexChanged) {
      GitIndexFileSystemRefresher.refreshRoots(myRepository.getProject(), Collections.singletonList(myRepository.getRoot()));
    }
  }

  private boolean isRootDirChange(@NotNull VFileEvent event) {
    VirtualFile file = event.getFile();
    if (file == null) return true; // can't do fast check, fallback to paths matching
    for (VirtualFile dir : myRootDirs) {
      if (VfsUtilCore.isAncestor(dir, file, false)) return true;
    }
    return false;
  }

  private void visitSubDirsInVfs() {
    for (VirtualFile rootDir : myRepositoryFiles.getRootDirs()) {
      rootDir.getChildren();
    }
    for (String path : myRepositoryFiles.getPathsToWatch()) {
      DvcsUtil.ensureAllChildrenInVfs(LocalFileSystem.getInstance().refreshAndFindFileByPath(path));
    }
  }
}
