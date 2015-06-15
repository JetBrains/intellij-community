/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Listens to .hg service files changes and updates {@link HgRepository} when needed.
 */
final class HgRepositoryUpdater implements Disposable, BulkFileListener {
  @NotNull private final HgRepositoryFiles myRepositoryFiles;
  @Nullable private final MessageBusConnection myMessageBusConnection;
  @NotNull private final QueueProcessor<Object> myUpdateQueue;
  @Nullable private final VirtualFile myBranchHeadsDir;
  @Nullable private VirtualFile myMqDir;
  @Nullable private final LocalFileSystem.WatchRequest myWatchRequest;
  @NotNull private final QueueProcessor<Object> myUpdateConfigQueue;


  HgRepositoryUpdater(@NotNull final HgRepository repository) {
    VirtualFile hgDir = repository.getHgDir();
    myWatchRequest = LocalFileSystem.getInstance().addRootToWatch(hgDir.getPath(), true);
    myRepositoryFiles = HgRepositoryFiles.getInstance(hgDir);
    DvcsUtil.visitVcsDirVfs(hgDir, HgRepositoryFiles.getSubDirRelativePaths());

    myBranchHeadsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getBranchHeadsDirPath());
    myMqDir = VcsUtil.getVirtualFile(myRepositoryFiles.getMQDirPath());

    Project project = repository.getProject();
    myUpdateQueue = new QueueProcessor<Object>(new DvcsUtil.Updater(repository), project.getDisposed());
    myUpdateConfigQueue = new QueueProcessor<Object>(new Consumer<Object>() {
      @Override
      public void consume(Object dummy) {
        repository.updateConfig();
      }
    }, project.getDisposed());
    if (!project.isDisposed()) {
      myMessageBusConnection = project.getMessageBus().connect();
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
  public void before(@NotNull List<? extends VFileEvent> events) {
    // everything is handled in #after()
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
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

    boolean configHgrcChanged = false;
    for (VFileEvent event : events) {
      String filePath = event.getPath();
      if (filePath == null) {
        continue;
      }
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
    }

    if (branchHeadsChanged || branchFileChanged || dirstateFileChanged || mergeFileChanged || rebaseFileChanged ||
        bookmarksFileChanged || currentBookmarkFileChanged || tagsFileChanged || localTagsFileChanged ||
        mqChanged) {
      myUpdateQueue.add(Void.TYPE);
    }
    if (configHgrcChanged) {
      myUpdateConfigQueue.add(Void.TYPE);
    }
  }
}
