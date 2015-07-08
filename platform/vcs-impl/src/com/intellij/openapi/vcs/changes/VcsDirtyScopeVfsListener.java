/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vcs.ConstantZipperUpdater;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Listens to file system events and notifies VcsDirtyScopeManagers responsible for changed files to mark these files dirty.
 *
 * @author Irina Chernushina
 * @author Kirill Likhodedov
 */
public class VcsDirtyScopeVfsListener implements ApplicationComponent, BulkFileListener {
  private final ProjectLocator myProjectLocator;
  private final MessageBusConnection myMessageBusConnection;
  // for tests only
  private boolean myForbid;
  private final ConstantZipperUpdater myZipperUpdater;
  private final List<FileAndDirsCollector> myQueue;
  private final Object myLock;
  private final Runnable myDirtReporter;

  public VcsDirtyScopeVfsListener() {
    myProjectLocator = ProjectLocator.getInstance();
    myMessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
    myLock = new Object();
    myQueue = new ArrayList<FileAndDirsCollector>();
    myDirtReporter = new Runnable() {
      @Override
      public void run() {
        ArrayList<FileAndDirsCollector> list;
        synchronized (myLock) {
          list = new ArrayList<FileAndDirsCollector>(myQueue);
          myQueue.clear();
        }
        Map<VcsDirtyScopeManager, Couple<HashSet<FilePath>>> map =
          new HashMap<VcsDirtyScopeManager, Couple<HashSet<FilePath>>>();
        for (FileAndDirsCollector collector : list) {
          Map<VcsDirtyScopeManager, Couple<HashSet<FilePath>>> pairMap =
            collector.map;
          for (Map.Entry<VcsDirtyScopeManager, Couple<HashSet<FilePath>>> entry : pairMap
            .entrySet()) {
            final VcsDirtyScopeManager key = entry.getKey();
            Couple<HashSet<FilePath>> existing = map.get(key);
            Couple<HashSet<FilePath>> value = entry.getValue();
            if (existing != null) {
              existing.getFirst().addAll(value.getFirst());
              existing.getSecond().addAll(value.getSecond());
            }
            else {
              map.put(key, value);
            }
          }
        }
        new FileAndDirsCollector().markDirty(map);
      }
    };
    myZipperUpdater = new ConstantZipperUpdater(300, Alarm.ThreadToUse.POOLED_THREAD, ApplicationManager.getApplication(),
                                                myDirtReporter);
  }

  public void setForbid(boolean forbid) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myForbid = forbid;
  }

  public void flushDirt() {
    myDirtReporter.run();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return VcsDirtyScopeVfsListener.class.getName();
  }

  @Override
  public void initComponent() {
    myMessageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  @Override
  public void disposeComponent() {
    myMessageBusConnection.disconnect();
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    if (myForbid) return;
    final FileAndDirsCollector dirtyFilesAndDirs = new FileAndDirsCollector();
    // collect files and directories - sources of events
    for (VFileEvent event : events) {
      if (event instanceof VFileCreateEvent) continue;
      final VirtualFile file = event.getFile();

      if (file == null || !file.isInLocalFileSystem()) {
        continue;
      }

      if (event instanceof VFileDeleteEvent || event instanceof VFileMoveEvent || event instanceof VFilePropertyChangeEvent) {
        dirtyFilesAndDirs.add(file);
      }
    }
    // and notify VCSDirtyScopeManager
    markDirtyOnPooled(dirtyFilesAndDirs);
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    if (myForbid) return;
    final FileAndDirsCollector dirtyFilesAndDirs = new FileAndDirsCollector();
    // collect files and directories - sources of events
    for (VFileEvent event : events) {
      if (event instanceof VFileDeleteEvent) continue;

      final VirtualFile file = event.getFile();
      if (file == null || !file.isInLocalFileSystem()) {
        continue;
      }

      if (event instanceof VFileContentChangeEvent || event instanceof VFileCopyEvent || event instanceof VFileCreateEvent ||
          event instanceof VFileMoveEvent) {
        dirtyFilesAndDirs.add(file);
      } else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent) event;

        if (pce.getPropertyName().equals(VirtualFile.PROP_NAME)) {
          // if a file was renamed, then the file is dirty and its parent directory is dirty too;
          // if a directory was renamed, all its children are recursively dirty, the parent dir is also dirty but not recursively.
          dirtyFilesAndDirs.add(file);   // the file is dirty recursively
          dirtyFilesAndDirs.addToFiles(file.getParent()); // directory is dirty alone. if parent is null - is checked in the method
        } else {
          dirtyFilesAndDirs.addToFiles(file);
        }
      }
    }
    // and notify VCSDirtyScopeManager
    markDirtyOnPooled(dirtyFilesAndDirs);
  }

  private void markDirtyOnPooled(final FileAndDirsCollector dirtyFilesAndDirs) {
    synchronized (myLock) {
      myQueue.add(dirtyFilesAndDirs);
    }
    myZipperUpdater.request();
  }

  /**
   * Stores VcsDirtyScopeManagers and files and directories which should be marked dirty by them.
   * Files will be marked dirty, directories will be marked recursively dirty, so if you need to mark dirty a directory, but
   * not recursively, you should add it to files.
   */
  private class FileAndDirsCollector {
    // dirty scope manager -> Pair(set of dirty files, set of dirty directories)
    Map<VcsDirtyScopeManager, Couple<HashSet<FilePath>>> map =
      new HashMap<VcsDirtyScopeManager, Couple<HashSet<FilePath>>>();

    /**
     * For the given VirtualFile constructs a FilePathImpl object without referring to the initial VirtualFile object
     * and adds this FilePathImpl to the set of files for proper VcsDirtyScopeManager - to mark these files dirty
     * when the set will be populated.
     * @param file        file which path is to be added.
     * @param addToFiles  If true, then add to dirty files even if it is a directory. Otherwise add to the proper set.
     */
    private void add(VirtualFile file, boolean addToFiles) {
      if (file == null) { return; }
      final boolean isDirectory = file.isDirectory();
      FilePath path = VcsUtil.getFilePath(file.getPath(), isDirectory);
      final Collection<VcsDirtyScopeManager> managers = getManagers(file);
      for (VcsDirtyScopeManager manager : managers) {
        Couple<HashSet<FilePath>> filesAndDirs = map.get(manager);
        if (filesAndDirs == null) {
          filesAndDirs = Couple.of(new HashSet<FilePath>(), new HashSet<FilePath>());
          map.put(manager, filesAndDirs);
        }

        if (addToFiles || !isDirectory) {
          filesAndDirs.first.add(path);
        } else {
          filesAndDirs.second.add(path);
        }
      }
    }

    /**
     * Adds files to the collection of files and directories - to the collection of directories (which are handled recursively).
     */
    private void add(VirtualFile file) {
      add(file, false);
    }

    /**
     * Adds to the collection of files. A file (even if it is a directory) is marked dirty alone (not recursively).
     * Use this method, when you want directory not to be marked dirty recursively.
     */
    private void addToFiles(VirtualFile file) {
      add(file, true);
    }

    private void markDirty(final Map<VcsDirtyScopeManager, Couple<HashSet<FilePath>>> outerMap) {
      for (Map.Entry<VcsDirtyScopeManager, Couple<HashSet<FilePath>>> entry : outerMap.entrySet()) {
        VcsDirtyScopeManager manager = entry.getKey();
        HashSet<FilePath> files = entry.getValue().first;
        HashSet<FilePath> dirs = entry.getValue().second;
        manager.filePathsDirty(files, dirs);
      }
    }
  }

  /**
   * Returns all VcsDirtyScopeManagers which serve the given file.
   * There may be none of them or there may be several (if a file is contained in several open projects, for instance),
   * though usually there is one.
   */
  @NotNull
  private Collection<VcsDirtyScopeManager> getManagers(final VirtualFile file) {
    final Collection<VcsDirtyScopeManager> result = new HashSet<VcsDirtyScopeManager>();
    if (file == null) { return result; }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final Collection<Project> projects = myProjectLocator.getProjectsForFile(file);
        for (Project project : projects) {
          final VcsDirtyScopeManager manager = VcsDirtyScopeManager.getInstance(project);
          if (manager != null && project.isInitialized()) {
            result.add(manager);
          }
        }
      }
    });
    return result;
  }
}
