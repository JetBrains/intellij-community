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
package org.zmlx.hg4idea;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Watches for events inside .hg directory of all registered roots.
 *
 * @author Kirill Likhodedov
 */
public class HgRepositoryWatcher extends AbstractProjectComponent implements BulkFileListener, VcsListener {

  private final Collection<LocalFileSystem.WatchRequest> myWatchRequests = new HashSet<LocalFileSystem.WatchRequest>();
  private final Collection<VirtualFile> myRoots = new HashSet<VirtualFile>();

  private VcsDirtyScopeManager myDirtyScopeManager;
  private ProjectLevelVcsManager myVcsManager;
  private AbstractVcs myVcs;

  protected HgRepositoryWatcher(@NotNull Project project) {
    super(project);
  }

  @Override
  public void initComponent() {
    MessageBus messageBus = myProject.getMessageBus();
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    messageBus.connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = HgVcs.getInstance(myProject);
  }

  @Override
  public void disposeComponent() {
    LocalFileSystem.getInstance().removeWatchedRoots(myWatchRequests);
    myWatchRequests.clear();
    myRoots.clear();
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      VirtualFile root = getRootForIndexFile(file);
      if (root != null) {
        myDirtyScopeManager.dirDirtyRecursively(root);
      }
      root = getRootForChangeBranch(file);
      if (root != null) {
        myProject.getMessageBus().syncPublisher(HgVcs.REMOTE_TOPIC).update(myProject, root);
        myProject.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(myProject, root);
      }
    }
  }

  @Nullable
  private VirtualFile getRootForIndexFile(@Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }
    for (VirtualFile root : myRoots) {
      if (isDirstateFile(file, root) || isUndoDirstateFile(file, root)) { // hg rollback doesn't modify dirstate, only undo.dirstate
        return root;
      }
    }
    return null;
  }

  private static boolean isDirstateFile(VirtualFile file, VirtualFile root) {
    return FileUtil.pathsEqual(root.getPath() + "/.hg/dirstate", file.getPath());
  }

  private static boolean isUndoDirstateFile(VirtualFile file, VirtualFile root) {
    return FileUtil.pathsEqual(root.getPath() + "/.hg/undo.dirstate", file.getPath());
  }

  @Nullable
  private VirtualFile getRootForChangeBranch(@Nullable VirtualFile file) {
    if (file == null) {
      return null;
    }
    for (VirtualFile root : myRoots) {
      if (isChangeBranchFile(file, root) || isUndoChangeBranchFile(file, root)) {
        return root;
      }
    }
    return null;
  }

  private static boolean isChangeBranchFile(VirtualFile file, VirtualFile root) {
    return FileUtil.pathsEqual(root.getPath() + "/.hg/branch", file.getPath());
  }

  private static boolean isUndoChangeBranchFile(VirtualFile file, VirtualFile root) {
    return FileUtil.pathsEqual(root.getPath() + "/.hg/undo.branch", file.getPath());
  }

  private void registerRoot(@NotNull VirtualFile root) {
    myWatchRequests.add(LocalFileSystem.getInstance().addRootToWatch(root.getPath(), true));
    myRoots.add(root);
  }

  @Override
  public void directoryMappingChanged() {
    for (VirtualFile root : myVcsManager.getRootsUnderVcs(myVcs)) {
      registerRoot(root);
    }
  }
}
