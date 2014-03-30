/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.dvcs.repo.RepositoryUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Listens to .git service files changes and updates {@link GitRepository} when needed.
 * @author Kirill Likhodedov
 */
final class GitRepositoryUpdater implements Disposable, BulkFileListener {
  @NotNull private final GitRepositoryFiles myRepositoryFiles;
  @Nullable private final MessageBusConnection myMessageBusConnection;
  @NotNull private final QueueProcessor<Object> myUpdateQueue;
  @NotNull private final Object DUMMY_UPDATE_OBJECT = new Object();
  @Nullable private final VirtualFile myRemotesDir;
  @Nullable private final VirtualFile myHeadsDir;
  @Nullable private final LocalFileSystem.WatchRequest myWatchRequest;


  GitRepositoryUpdater(@NotNull GitRepository repository) {
    VirtualFile gitDir = repository.getGitDir();
    myWatchRequest = LocalFileSystem.getInstance().addRootToWatch(gitDir.getPath(), true);

    myRepositoryFiles = GitRepositoryFiles.getInstance(gitDir);
    RepositoryUtil.visitVcsDirVfs(gitDir, GitRepositoryFiles.getSubDirRelativePaths());
    myHeadsDir = VcsUtil.getVirtualFile(myRepositoryFiles.getRefsHeadsPath());
    myRemotesDir = VcsUtil.getVirtualFile(myRepositoryFiles.getRefsRemotesPath());

    Project project = repository.getProject();
    myUpdateQueue = new QueueProcessor<Object>(new RepositoryUtil.Updater(repository), project.getDisposed());
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
    // which files in .git were changed
    boolean configChanged = false;
    boolean headChanged = false;
    boolean branchFileChanged = false;
    boolean packedRefsChanged = false;
    boolean rebaseFileChanged = false;
    boolean mergeFileChanged = false;
    for (VFileEvent event : events) {
      String filePath = event.getPath();
      if (filePath == null) {
        continue;
      }
      filePath = GitFileUtils.stripFileProtocolPrefix(filePath);
      if (myRepositoryFiles.isConfigFile(filePath)) {
        configChanged = true;
      } else if (myRepositoryFiles.isHeadFile(filePath)) {
        headChanged = true;
      } else if (myRepositoryFiles.isBranchFile(filePath)) {
        // it is also possible, that a local branch with complex name ("folder/branch") was created => the folder also to be watched.
        branchFileChanged = true;
        RepositoryUtil.visitAllChildrenRecursively(myHeadsDir);
      } else if (myRepositoryFiles.isRemoteBranchFile(filePath)) {
        // it is possible, that a branch from a new remote was fetch => we need to add new remote folder to the VFS
        branchFileChanged = true;
        RepositoryUtil.visitAllChildrenRecursively(myRemotesDir);
      } else if (myRepositoryFiles.isPackedRefs(filePath)) {
        packedRefsChanged = true;
      } else if (myRepositoryFiles.isRebaseFile(filePath)) {
        rebaseFileChanged = true;
      } else if (myRepositoryFiles.isMergeFile(filePath)) {
        mergeFileChanged = true;
      }
    }

    if (headChanged || configChanged || branchFileChanged || packedRefsChanged || rebaseFileChanged || mergeFileChanged) {
      myUpdateQueue.add(DUMMY_UPDATE_OBJECT);
    }
  }

}
