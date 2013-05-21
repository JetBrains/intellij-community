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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Listens to .hg service files changes and updates {@link HgRepository} when needed.
 *
 * @author Nadya Zabrodina
 */
final class HgRepositoryUpdater implements Disposable, BulkFileListener {
  private final HgRepositoryFiles myRepositoryFiles;
  private final MessageBusConnection myMessageBusConnection;
  private final QueueProcessor<Object> myUpdateQueue;
  private final Object DUMMY_UPDATE_OBJECT = new Object();
  private final VirtualFile myBranchHeadsDir;
  private final LocalFileSystem.WatchRequest myWatchRequest;


  HgRepositoryUpdater(HgRepository repository) {
    VirtualFile hgDir = repository.getHgDir();
    myWatchRequest = LocalFileSystem.getInstance().addRootToWatch(hgDir.getPath(), true);
    myRepositoryFiles = HgRepositoryFiles.getInstance(hgDir);
    visitHgDirVfs(hgDir);

    myBranchHeadsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getBranchHeadsPath());

    Project project = repository.getProject();
    myUpdateQueue = new QueueProcessor<Object>(new Updater(repository), project.getDisposed());
    if (!project.isDisposed()) {
      myMessageBusConnection = project.getMessageBus().connect();
      myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }
    else {
      myMessageBusConnection = null;
    }
  }

  private static void visitHgDirVfs(@NotNull VirtualFile hgDir) {
    hgDir.getChildren();
    for (String subdir : HgRepositoryFiles.getSubDirRelativePaths()) {
      VirtualFile dir = hgDir.findFileByRelativePath(subdir);
      // process recursively, because we need to visit all branches under cache/branchheads
      visitAllChildrenRecursively(dir);
    }
  }

  private static void visitAllChildrenRecursively(@Nullable VirtualFile dir) {
    if (dir == null) {
      return;
    }
    VfsUtil.processFilesRecursively(dir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        return true;
      }
    });
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
    boolean mergeFileChanged = false;
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
        visitAllChildrenRecursively(myBranchHeadsDir);
      }
      else if (myRepositoryFiles.isMergeFile(filePath)) {
        mergeFileChanged = true;
      }
    }


    if (branchHeadsChanged || branchFileChanged || mergeFileChanged) {
      myUpdateQueue.add(DUMMY_UPDATE_OBJECT);
    }
  }

  private static class Updater implements Consumer<Object> {
    private final HgRepository myRepository;

    public Updater(HgRepository repository) {
      myRepository = repository;
    }

    @Override
    public void consume(Object dummy) {
      myRepository.update();
    }
  }
}
