// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.DebouncedUpdates;
import com.intellij.util.ui.update.UpdateQueue;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;

import java.util.List;

/**
 * Listens to .hg service files changes and updates {@link HgRepository} when needed.
 */
final class HgRepositoryUpdater implements Disposable, BulkFileListener {
  private final Project myProject;
  private final @NotNull HgRepositoryFiles myRepositoryFiles;
  private final @Nullable MessageBusConnection myMessageBusConnection;
  private final @NotNull UpdateQueue<Unit> myUpdateQueue;
  private final @Nullable VirtualFile myBranchHeadsDir;
  private static final int TIME_SPAN = 300;
  private @Nullable VirtualFile myMqDir;
  private final @Nullable LocalFileSystem.WatchRequest myWatchRequest;
  private final @NotNull UpdateQueue<Unit> myUpdateConfigQueue;
  private final HgRepository myRepository;
  private final VcsDirtyScopeManager myDirtyScopeManager;


  HgRepositoryUpdater(final @NotNull HgRepository repository, final CoroutineScope coroutineScope) {
    myRepository = repository;
    VirtualFile hgDir = myRepository.getHgDir();
    myWatchRequest = LocalFileSystem.getInstance().addRootToWatch(hgDir.getPath(), true);
    myRepositoryFiles = HgRepositoryFiles.getInstance(hgDir);
    DvcsUtil.visitVcsDirVfs(hgDir, HgRepositoryFiles.getSubDirRelativePaths());

    myBranchHeadsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getBranchHeadsDirPath());
    myMqDir = VcsUtil.getVirtualFile(myRepositoryFiles.getMQDirPath());

    myProject = repository.getProject();
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    myUpdateQueue = DebouncedUpdates.<Unit>forScope(coroutineScope, "HgRepositoryUpdate", TIME_SPAN)
      .withContext(Dispatchers.getDefault())
      .runLatest(ignored -> myRepository.update())
      .cancelOnDispose(this);
    myUpdateConfigQueue = DebouncedUpdates.<Unit>forScope(coroutineScope, "HgConfigUpdate", TIME_SPAN)
      .withContext(Dispatchers.getDefault())
      .runLatest(ignored -> myRepository.updateConfig())
      .cancelOnDispose(this);

    if (!myProject.isDisposed()) {
      myMessageBusConnection = myProject.getMessageBus().connect(this);
      myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }
    else {
      myMessageBusConnection = null;
    }
  }

  @Override
  public void dispose() {
    if (myWatchRequest != null) {
      LocalFileSystem.getInstance().removeWatchedRoot(myWatchRequest);
    }
    if (myMessageBusConnection != null) {
      myMessageBusConnection.disconnect();
    }
  }

  @Override
  public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
    // which files in .hg were changed
    boolean branchHeadsChanged = false;
    boolean branchFileChanged = false;
    boolean dirstateFileChanged = false;
    boolean mergeFileChanged = false;
    boolean rebaseFileChanged = false;
    boolean bookmarksFileChanged = false;
    boolean tagsFileChanged = false;
    boolean localTagsFileChanged = false;
    boolean currentBookmarkFileChanged = false;
    boolean mqChanged = false;
    boolean hgIgnoreChanged = false;

    boolean configHgrcChanged = false;
    for (VFileEvent event : events) {
      String filePath = event.getPath();
      if (myRepositoryFiles.isbranchHeadsFile(filePath)) {
        branchHeadsChanged = true;
      }
      else if (myRepositoryFiles.isBranchFile(filePath)) {
        branchFileChanged = true;
        DvcsUtil.ensureAllChildrenInVfs(myBranchHeadsDir);
      }
      else if (myRepositoryFiles.isDirstateFile(filePath)) {
        dirstateFileChanged = true;
      }
      else if (myRepositoryFiles.isMergeFile(filePath)) {
        mergeFileChanged = true;
      }
      else if (myRepositoryFiles.isRebaseFile(filePath)) {
        rebaseFileChanged = true;
      }
      else if (myRepositoryFiles.isBookmarksFile(filePath)) {
        bookmarksFileChanged = true;
      }
      else if (myRepositoryFiles.isTagsFile(filePath)) {
        tagsFileChanged = true;
      }
      else if (myRepositoryFiles.isLocalTagsFile(filePath)) {
        localTagsFileChanged = true;
      }
      else if (myRepositoryFiles.isCurrentBookmarksFile(filePath)) {
        currentBookmarkFileChanged = true;
      }
      else if (myRepositoryFiles.isMqFile(filePath)) {
        mqChanged = true;
        if (myMqDir == null) {
          myMqDir = VcsUtil.getVirtualFile(myRepositoryFiles.getMQDirPath());
        }
        DvcsUtil.ensureAllChildrenInVfs(myMqDir);
      }
      else if (myRepositoryFiles.isConfigHgrcFile(filePath)) {
        configHgrcChanged = true;
      }
      else if (myRepositoryFiles.isHgIgnore(filePath)) {
        hgIgnoreChanged = true;
      }
    }

    if (branchHeadsChanged || branchFileChanged || dirstateFileChanged || mergeFileChanged || rebaseFileChanged ||
        bookmarksFileChanged || currentBookmarkFileChanged || tagsFileChanged || localTagsFileChanged ||
        mqChanged) {
      myUpdateQueue.queue(Unit.INSTANCE);
    }
    if (configHgrcChanged) {
      myUpdateConfigQueue.queue(Unit.INSTANCE);
    }
    if (dirstateFileChanged || hgIgnoreChanged) {
      myRepository.getIgnoredFilesHolder().startRescan();
      final VirtualFile root = myRepository.getRoot();
      myDirtyScopeManager.dirDirtyRecursively(root);
      if (dirstateFileChanged) {
        //update async incoming/outgoing model
        BackgroundTaskUtil.syncPublisher(myProject, HgVcs.REMOTE_TOPIC).update(myProject, root);
      }
    }
  }

}
