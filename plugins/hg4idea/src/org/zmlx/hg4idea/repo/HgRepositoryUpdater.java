/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
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
  @NotNull private final HgRepositoryFiles myRepositoryFiles;
  @Nullable private final MessageBusConnection myMessageBusConnection;
  @NotNull private final MergingUpdateQueue myUpdateQueue;
  @Nullable private final VirtualFile myBranchHeadsDir;
  private static final int TIME_SPAN = 300;
  @Nullable private VirtualFile myMqDir;
  @Nullable private final LocalFileSystem.WatchRequest myWatchRequest;
  @NotNull private final MergingUpdateQueue myUpdateConfigQueue;
  private final HgRepository myRepository;
  private final VcsDirtyScopeManager myDirtyScopeManager;


  HgRepositoryUpdater(@NotNull final HgRepository repository) {
    myRepository = repository;
    VirtualFile hgDir = myRepository.getHgDir();
    myWatchRequest = LocalFileSystem.getInstance().addRootToWatch(hgDir.getPath(), true);
    myRepositoryFiles = HgRepositoryFiles.getInstance(hgDir);
    DvcsUtil.visitVcsDirVfs(hgDir, HgRepositoryFiles.getSubDirRelativePaths());

    myBranchHeadsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getBranchHeadsDirPath());
    myMqDir = VcsUtil.getVirtualFile(myRepositoryFiles.getMQDirPath());

    myProject = repository.getProject();
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myUpdateQueue = new MergingUpdateQueue("HgRepositoryUpdate", TIME_SPAN, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD);
    myUpdateConfigQueue = new MergingUpdateQueue("HgConfigUpdate", TIME_SPAN, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD);
    if (!myProject.isDisposed()) {
      myMessageBusConnection = myProject.getMessageBus().connect();
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
    myUpdateQueue.cancelAllUpdates();
    myUpdateConfigQueue.cancelAllUpdates();
    if (myMessageBusConnection != null) {
      myMessageBusConnection.disconnect();
    }
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
    boolean hgIgnoreChanged = false;

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
      else if (myRepositoryFiles.isHgIgnore(filePath)) {
        hgIgnoreChanged = true;
      }
    }

    if (branchHeadsChanged || branchFileChanged || dirstateFileChanged || mergeFileChanged || rebaseFileChanged ||
        bookmarksFileChanged || currentBookmarkFileChanged || tagsFileChanged || localTagsFileChanged ||
        mqChanged) {
      myUpdateQueue.queue(new MyUpdater("hgrepositoryUpdate"));
    }
    if (configHgrcChanged) {
      myUpdateConfigQueue.queue(new MyUpdater("hgconfigUpdate"){
        @Override
        public void run() {
          myRepository.updateConfig();
        }
      });
    }
    if (dirstateFileChanged || hgIgnoreChanged) {
      myRepository.getLocalIgnoredHolder().startRescan();
      final VirtualFile root = myRepository.getRoot();
      myDirtyScopeManager.dirDirtyRecursively(root);
      if (dirstateFileChanged) {
        //update async incoming/outgoing model
        BackgroundTaskUtil.syncPublisher(myProject, HgVcs.REMOTE_TOPIC).update(myProject, root);
      }
    }
  }

  private class MyUpdater extends Update {
    public MyUpdater(String name) {
      super(name);
    }

    @Override
    public boolean canEat(Update update) {
      return true;
    }

    @Override
    public void run() {
      myRepository.update();
    }
  }
}
