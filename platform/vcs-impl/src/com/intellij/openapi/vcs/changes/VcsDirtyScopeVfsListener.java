/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Listens to file system events and notifies VcsDirtyScopeManagers responsible for changed files to mark these
 * files dirty.
 * @author Irina Chernushina
 * @author Kirill Likhodedov
 */
public class VcsDirtyScopeVfsListener implements ApplicationComponent, BulkFileListener {
  private final ProjectLocator myProjectLocator;
  private final MessageBusConnection myMessageBusConnection;

  public VcsDirtyScopeVfsListener() {
    myProjectLocator = ProjectLocator.getInstance();
    myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
  }

  @NotNull
  public String getComponentName() {
    return VcsDirtyScopeVfsListener.class.getName();
  }

  public void initComponent() {
    myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  public void disposeComponent() {
    myMessageBusConnection.disconnect();
  }

  @Override
  public void before(List<? extends VFileEvent> events) {
    final FileAndDirsCollector dirtyFilesAndDirs = new FileAndDirsCollector();
    // collect files and directories - sources of events
    for (VFileEvent event : events) {
      final String path = event.getPath();
      if (event instanceof VFileDeleteEvent) {
        final VirtualFile file = ((VFileDeleteEvent)event).getFile();
        if (!file.isInLocalFileSystem()) { return; }
        dirtyFilesAndDirs.add(file);
      } else if (event instanceof VFileMoveEvent) {
        final VirtualFile file = ((VFileMoveEvent)event).getFile();
        dirtyFilesAndDirs.addToFiles(file);
      } else if (event instanceof VFilePropertyChangeEvent) {
        final VirtualFile file = ((VFilePropertyChangeEvent)event).getFile();
        dirtyFilesAndDirs.addToFiles(file);
      }
    }
    dirtyFilesAndDirs.markDirty();
  }

  @Override
  public void after(List<? extends VFileEvent> events) {
    final FileAndDirsCollector dirtyFilesAndDirs = new FileAndDirsCollector();

    for (VFileEvent event : events) {
      if (event instanceof VFileContentChangeEvent) {
        final VirtualFile file = ((VFileContentChangeEvent)event).getFile();
        dirtyFilesAndDirs.addToFiles(file);
      } else if (event instanceof VFileCopyEvent) {
        final VirtualFile file = ((VFileCopyEvent)event).getFile();
        dirtyFilesAndDirs.addToFiles(file);
      } else if (event instanceof VFileCreateEvent) {
        final VFileCreateEvent ce = ((VFileCreateEvent)event);
        final VirtualFile file = ce.getParent().findChild(ce.getChildName());
        dirtyFilesAndDirs.addToFiles(file);
      } else if (event instanceof VFileMoveEvent) {
        dirtyFilesAndDirs.add(((VFileMoveEvent)event).getFile());
      } else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent) event;
        VirtualFile file = pce.getFile();
        // if file was renamed, then mark dirty the file and its parent directory
        if (pce.getPropertyName().equals(VirtualFile.PROP_NAME)) {
          if (file.getParent() != null) {
            file = file.getParent();
          }
          dirtyFilesAndDirs.add(file);
        } else {
          dirtyFilesAndDirs.addToFiles(file);
        }
      }
    }
    dirtyFilesAndDirs.markDirty();
  }

  @Nullable
  private VcsDirtyScopeManager getManager(final VirtualFile file) {
    if (file == null) { return null; }
    final Project project = myProjectLocator.guessProjectForFile(file);
    if (project == null) { return null; }
    return VcsDirtyScopeManager.getInstance(project);
  }

  /**
   * Stores VcsDirtyScopeManagers and files and directories which should be marked dirty by them.
   * Files will be marked dirty, directories will be marked recursively dirty, so if you need to mark dirty a directory, but
   * not recursively, you should add it to files.
   */
  private class FileAndDirsCollector {
    // dirty scope manager -> Pair(set of dirty files, set of dirty directories)
    Map<VcsDirtyScopeManager, Pair<HashSet<FilePath>, HashSet<FilePath>>> map =
      new HashMap<VcsDirtyScopeManager, Pair<HashSet<FilePath>, HashSet<FilePath>>>();

    /**
     * For the given VirtualFile constructs a FilePathImpl object without referring to the initial VirtualFile object
     * and adds this FilePathImpl to the set of files for proper VcsDirtyScopeManager - to mark these files dirty
     * when the set will be populated.
     * @param file        file which path is to be added.
     * @param addToFiles  If true, then add to dirty files even if it is a directory. Otherwise add to the proper set.
     */
    private void add(VirtualFile file, boolean addToFiles) {
      final VcsDirtyScopeManager manager = getManager(file);
      if (manager == null) { return; }
      Pair<HashSet<FilePath>, HashSet<FilePath>> filesAndDirs = map.get(manager);
      if (filesAndDirs == null) {
        filesAndDirs = Pair.create(new HashSet<FilePath>(), new HashSet<FilePath>());
        map.put(manager, filesAndDirs);
      }

      final boolean isDirectory = file.isDirectory();
      // need to create FilePath explicitly without referring to VirtualFile because the path of VirtualFile may change
      final FilePathImpl path = new FilePathImpl(new File(file.getPath()), isDirectory);
      if (addToFiles || isDirectory) {
        filesAndDirs.first.add(path);
      } else {
        filesAndDirs.second.add(path);
      }
    }

    void add(VirtualFile file) {
      add(file, false);
    }

    void addToFiles(VirtualFile file) {
      add(file, true);
    }

    void markDirty() {
      for (Map.Entry<VcsDirtyScopeManager, Pair<HashSet<FilePath>, HashSet<FilePath>>> entry : map.entrySet()) {
        VcsDirtyScopeManager manager = entry.getKey();
        HashSet<FilePath> files = entry.getValue().first;
        HashSet<FilePath> dirs = entry.getValue().second;
        manager.filePathsDirty(files, dirs);
      }
    }
  }

}
