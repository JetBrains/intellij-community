/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBusConnection;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * Listens to the external changes of the given files under vcs root
 * and marks everything under this root dirty if this file changes.
 * This is intended to listen to repository service files (like .git/index for Git VCS or .hg/dirindex for Mercurial)
 * and update IDEA's information about file statuses in case of external operations with repository.
 * For example, when user commits from the command line IDEA should notice that and update the Changes View.
 * </p>
 * <p>
 * To avoid marking everything dirty in case of commit from IDEA (or other action), there are methods
 * {@link #internalOperationStarted()} and {@link #internalOperationEnded()}: the VCS plugin should call them before and after
 * each operation that would change files which are listened. Thus the change (for example, commit from IDEA) will be treated
 * as internal and files won't be marked dirty (the Changes View is updated after IDEA commit anyway).
 * </p>
 * <p>
 * Use the constructor to create the listener, and {@link #activate()}/{@link #dispose()} to subscribe/unsubscribe to events.
 * </p>
 * @author Kirill Likhodedov
 */
public class RepositoryChangeListener extends VirtualFileAdapter implements VcsListener {
  private final Project myProject;
  private final String[] myRelativePathsToListen;
  private final AtomicBoolean myInternalChangeInProgress = new AtomicBoolean();
  private final AtomicLong myInternalChangeEndTime = new AtomicLong();
  private final MessageBusConnection myConnection;

  /**
   * @param relativePathsToListen Paths to files (directories are not supported yet) which are to be listened.
   * Paths are relative to vcs roots.
   */
  public RepositoryChangeListener(Project project, String... relativePathsToListen) {
    myProject = project;
    myRelativePathsToListen = relativePathsToListen;
    myConnection = myProject.getMessageBus().connect();
  }

  public void activate() {
    loadRepoFilesForAllMappings(myRelativePathsToListen);
    myConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    VirtualFileManager.getInstance().addVirtualFileListener(this);
  }

  public void dispose() {
    myConnection.disconnect();
    VirtualFileManager.getInstance().removeVirtualFileListener(this);
  }

  @Override
  public void directoryMappingChanged() {
    loadRepoFilesForAllMappings(myRelativePathsToListen);
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    final String path = event.getFile().getPath();
    for (String relativePath : myRelativePathsToListen) {
      if (path.endsWith(relativePath)) {
        if (!myInternalChangeInProgress.get() && !internalChangeHappenedRecently()) {
          // identify the vcs root for this file
          VirtualFile vcsRoot = null;
          for (VcsRoot root : ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots()) {
            if ((root.path.getPath() + "/" + relativePath).equals(path)) {
              vcsRoot = root.path;
              break;
            }
          }
          VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(vcsRoot);
        }
        break;
      }
    }
  }

  /**
   * Notifies the listener that IDEA is going to change index file right away.
   * For example, at the beginning of the commit process.
   */
  public void internalOperationStarted() {
    myInternalChangeInProgress.set(true);
  }

  /**
   * Notifies the listener that IDEA has finished changing index file.
   */
  public void internalOperationEnded() {
    // no synchronization here, because it's not dangerous if index change time is a bit different.
    myInternalChangeInProgress.set(false);
    myInternalChangeEndTime.set(System.currentTimeMillis());
  }

  /**
   * @return true if last index change via IDEA happened less than a second ago.
   */
  private boolean internalChangeHappenedRecently() {
    return System.currentTimeMillis() - myInternalChangeEndTime.get() < 1000;
  }

  // load repository files for all repositories (and thus subscribe to changes in them)
  private void loadRepoFilesForAllMappings(String[] relativePathsToListen) {
    for (VcsRoot root : ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots()) {
      for (String path : relativePathsToListen) {
        loadRepoFile(root.path, path);
      }
    }
  }

  /**
   * Loads the file for the given repository root,
   * so that the correspondent VirtualFile is created and thus changes to this file will be fired to the listener.
   */
  private void loadRepoFile(VirtualFile vcsRoot, String relativePath) {
    if (vcsRoot != null) {
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(vcsRoot.getPath(), relativePath));
    }
  }

}
