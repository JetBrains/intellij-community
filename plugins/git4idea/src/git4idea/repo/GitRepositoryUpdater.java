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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;

import java.util.List;

/**
 * Listens to .git service files changes and updates {@link GitRepository} when needed.
 * @author Kirill Likhodedov
 */
final class GitRepositoryUpdater implements Disposable, BulkFileListener {

  private final GitRepository myRepository;
  private MessageBusConnection myMessageBusConnection;

  private final String myHeadFilePath;
  private final String myMergeHeadPath;
  private final String myRebaseApplyPath;
  private final String myRebaseMergePath;
  private final String myPackedRefsPath;
  private final String myRefsHeadsDirPath;

  GitRepositoryUpdater(GitRepository repository) {
    myRepository = repository;

    // add .git/ and .git/refs/heads to the VFS
    VirtualFile gitDir = repository.getRoot().findChild(".git");
    assert gitDir != null;
    gitDir.getChildren();
    final VirtualFile refsHeadsDir = gitDir.findFileByRelativePath("refs/heads");
    assert refsHeadsDir != null;
    refsHeadsDir.getChildren();

    // save paths of the files, that we will watch
    String gitDirPath = stripFileProtocolPrefix(gitDir.getPath());
    myHeadFilePath = gitDirPath + "/HEAD";
    myMergeHeadPath = gitDirPath + "/MERGE_HEAD";
    myRebaseApplyPath = gitDirPath + "/rebase-apply";
    myRebaseMergePath = gitDirPath + "/rebase-merge";
    myPackedRefsPath = gitDirPath + "/packed-refs";
    myRefsHeadsDirPath = gitDirPath + "/refs/heads";

    myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  private static String stripFileProtocolPrefix(String path) {
    final String FILE_PROTOCOL = "file://";
    if (path.startsWith(FILE_PROTOCOL)) {
      return path.substring(FILE_PROTOCOL.length());
    }
    return path;
  }

  @Override
  public void dispose() {
    myMessageBusConnection.disconnect();
  }

  @Override
  public void before(List<? extends VFileEvent> events) {
    // everything is handled in #after()
  }

  @Override
  public void after(List<? extends VFileEvent> events) {
    // which files in .git were changed
    boolean headChanged = false;
    boolean branchFileChanged = false;
    boolean packedRefsChanged = false;
    boolean rebaseFileChanged = false;
    boolean mergeFileChanged = false;
    for (VFileEvent event : events) {
      final VirtualFile file = event.getFile();
      if (file == null) {
        continue;
      }
      String filePath = stripFileProtocolPrefix(file.getPath());
      if (isHeadFile(filePath)) {
        headChanged = true;
      } else if (isBranchFile(filePath)) {
        branchFileChanged = true;
      } else if (isPackedRefs(filePath)) {
        packedRefsChanged = true;
      } else if (isRebaseFile(filePath)) {
        rebaseFileChanged = true;
      } else if (isMergeFile(filePath)) {
        mergeFileChanged = true;
      }
    }

    // what should be updated in GitRepository
    boolean updateCurrentBranch = false;
    boolean updateCurrentRevision = false;
    boolean updateState = false;
    if (headChanged) {
      updateCurrentBranch = true;
      updateCurrentRevision = true;
      updateState = true;
    }
    if (branchFileChanged) {
      updateCurrentRevision = true;
    }
    if (rebaseFileChanged || mergeFileChanged) {
      updateState = true;
    }

    // update GitRepository on pooled thread, because it requires reading from disk and parsing data.
    if (updateCurrentBranch) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.CURRENT_BRANCH);
        }
      });
    }
    if (updateCurrentRevision) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.CURRENT_REVISION);
        }
      });
    }
    if (updateState) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          myRepository.update(GitRepository.TrackedTopic.STATE);
        }
      });
    }
  }

  private boolean isHeadFile(String file) {
    return file.equals(myHeadFilePath);
  }

  private boolean isBranchFile(String filePath) {
    return filePath.startsWith(myRefsHeadsDirPath);
  }

  private boolean isRebaseFile(String path) {
    return path.equals(myRebaseApplyPath) || path.equals(myRebaseMergePath);
  }

  private boolean isMergeFile(String file) {
    return file.equals(myMergeHeadPath);
  }

  private boolean isPackedRefs(String file) {
    return file.equals(myPackedRefsPath);
  }

}
