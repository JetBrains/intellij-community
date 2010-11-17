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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBusConnection;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Listens to .dir/index and marks everything dirty if this file changes (for example, when external commit happens).
 * To avoid marking everything dirty in case of commit from IDEA (or other action), there are methods
 * {@link #internalIndexChangeStarted()} and {@link #internalIndexChangeEnded()}.
 * @author Kirill Likhodedov
 */
public class GitIndexChangeListener extends VirtualFileAdapter implements VcsListener {
  private final Project myProject;
  private final AtomicBoolean myInternalIndexChangeInProgress = new AtomicBoolean();
  private AtomicLong myInternalIndexChangeEndTime = new AtomicLong();
  private MessageBusConnection myConnection;

  public GitIndexChangeListener(Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect();
    // listen to .git/index files for all repositories
    loadIndexFilesForAllMappings();
    myConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    VirtualFileManager.getInstance().addVirtualFileListener(this);
  }

  public void dispose() {
    myConnection.disconnect();
    VirtualFileManager.getInstance().removeVirtualFileListener(this);
  }

  @Override
  public void directoryMappingChanged() {
    loadIndexFilesForAllMappings();
  }

  @Override
  public void contentsChanged(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    if (file.getParent().getName().equals(".git") && file.getName().equals("index")) {
      if (!myInternalIndexChangeInProgress.get() && !internalIndexChangeHappenedRecently()) {
        VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      }
    }
  }

  /**
   * Notifies the listener that IDEA is going to change index file right away.
   * For example, at the beginning of the commit process.
   */
  public void internalIndexChangeStarted() {
    myInternalIndexChangeInProgress.set(true);
  }

  /**
   * Notifies the listener that IDEA has finished changing index file.
   */
  public void internalIndexChangeEnded() {
    // no synchronization here, because it's not dangerous if index change time is a bit different.
    myInternalIndexChangeInProgress.set(false);
    myInternalIndexChangeEndTime.set(System.currentTimeMillis());
  }

  /**
   * @return true if last index change via IDEA happened less than a second ago.
   */
  private boolean internalIndexChangeHappenedRecently() {
    return System.currentTimeMillis() - myInternalIndexChangeEndTime.get() < 1000;
  }

  // load (and subscribe to changes) .git/index for all repositories
  private void loadIndexFilesForAllMappings() {
    for (VcsRoot root : ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots()) {
      loadIndexFile(root.path);
    }
  }

  /**
   * Loads .git/index file of the given git root,
   * so that the correspondent VirtualFile is created and thus changes to this file will be fired to the listener.
   */
  private static void loadIndexFile(VirtualFile vcsRoot) {
    if (vcsRoot != null) {
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(vcsRoot.getPath(), ".git/index"));
    }
  }

}
