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
package git4idea.repo;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.dvcs.DvcsUtil.ensureAllChildrenInVfs;
import static git4idea.repo.GitRepository.GIT_REPO_CHANGE;

/**
 * Listens to .git service files changes and updates {@link GitRepository} when needed.
 */
final class GitRepositoryUpdater implements Disposable, AsyncVfsEventsListener {

  @NotNull private final GitRepository myRepository;
  @NotNull private final GitRepositoryFiles myRepositoryFiles;
  @Nullable private final VirtualFile myRemotesDir;
  @Nullable private final VirtualFile myHeadsDir;
  @Nullable private final VirtualFile myTagsDir;
  @NotNull private final Set<LocalFileSystem.WatchRequest> myWatchRequests;

  GitRepositoryUpdater(@NotNull GitRepository repository, @NotNull GitRepositoryFiles gitFiles) {
    myRepository = repository;
    Collection<String> rootPaths = ContainerUtil.map(gitFiles.getRootDirs(), file -> file.getPath());
    myWatchRequests = LocalFileSystem.getInstance().addRootsToWatch(rootPaths, true);

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
  public void filesChanged(@NotNull List<VFileEvent> events) {
    // which files in .git were changed
    boolean configChanged = false;
    boolean headChanged = false;
    boolean branchFileChanged = false;
    boolean packedRefsChanged = false;
    boolean rebaseFileChanged = false;
    boolean mergeFileChanged = false;
    boolean tagChanged = false;
    for (VFileEvent event : events) {
      String filePath = GitFileUtils.stripFileProtocolPrefix(event.getPath());
      if (myRepositoryFiles.isConfigFile(filePath)) {
        configChanged = true;
      } else if (myRepositoryFiles.isHeadFile(filePath)) {
        headChanged = true;
      } else if (myRepositoryFiles.isBranchFile(filePath)) {
        // it is also possible, that a local branch with complex name ("folder/branch") was created => the folder also to be watched.
          branchFileChanged = true;
        ensureAllChildrenInVfs(myHeadsDir);
      } else if (myRepositoryFiles.isRemoteBranchFile(filePath)) {
        // it is possible, that a branch from a new remote was fetch => we need to add new remote folder to the VFS
        branchFileChanged = true;
        ensureAllChildrenInVfs(myRemotesDir);
      } else if (myRepositoryFiles.isPackedRefs(filePath)) {
        packedRefsChanged = true;
      } else if (myRepositoryFiles.isRebaseFile(filePath)) {
        rebaseFileChanged = true;
      } else if (myRepositoryFiles.isMergeFile(filePath)) {
        mergeFileChanged = true;
      } else if (myRepositoryFiles.isTagFile(filePath)) {
        ensureAllChildrenInVfs(myTagsDir);
        tagChanged = true;
      }
    }

    if (headChanged || configChanged || branchFileChanged || packedRefsChanged || rebaseFileChanged || mergeFileChanged) {
      myRepository.update();
    }
    if (tagChanged) {
      BackgroundTaskUtil.syncPublisher(myRepository.getProject(), GIT_REPO_CHANGE).repositoryChanged(myRepository);
    }
  }

  private void visitSubDirsInVfs() {
    for (VirtualFile rootDir : myRepositoryFiles.getRootDirs()) {
      rootDir.getChildren();
    }
    for (String path : myRepositoryFiles.getDirsToWatch()) {
      ensureAllChildrenInVfs(LocalFileSystem.getInstance().refreshAndFindFileByPath(path));
    }
  }
}
