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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.win32.Win32LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.Queue;

import java.util.*;

public class RefreshWorker {
  private final VirtualFile myRefreshRoot;
  private final boolean myIsRecursive;
  private final Queue<VirtualFile> myRefreshQueue = new Queue<VirtualFile>(100);

  private final List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  public RefreshWorker(final VirtualFile refreshRoot, final boolean isRecursive) {
    myRefreshRoot = refreshRoot;
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(refreshRoot);
  }

  public void scan() {
    final NewVirtualFile root = (NewVirtualFile)myRefreshRoot;
    NewVirtualFileSystem delegate = root.getFileSystem();
    if (root.isDirty() && !delegate.exists(root)) {
      scheduleDeletion(root);
      root.markClean();
    }
    else {
      if (delegate.getProtocol().equals(LocalFileSystem.PROTOCOL) && SystemInfo.isWindows && root.isDirectory() &&
         Registry.is("filesystem.useNative") && !ApplicationManager.getApplication().isUnitTestMode()) {
        delegate = Win32LocalFileSystem.getWin32Instance();
      }

      final PersistentFS persistence = (PersistentFS)ManagingFS.getInstance();

      while (!myRefreshQueue.isEmpty()) {
        final VirtualFileSystemEntry file = (VirtualFileSystemEntry)myRefreshQueue.pullFirst();
        if (!file.isDirty()) continue;

        if (file.isDirectory()) {
          VirtualDirectoryImpl dir = (VirtualDirectoryImpl)file;
          final boolean fullSync = dir.allChildrenLoaded();
          if (fullSync) {
            Set<String> currentNames = new HashSet<String>(Arrays.asList(persistence.list(file)));
            Set<String> uptodateNames = new HashSet<String>(Arrays.asList(VfsUtil.filterNames(delegate.list(file))));

            Set<String> newNames = new HashSet<String>(uptodateNames);
            newNames.removeAll(currentNames);

            Set<String> deletedNames = new HashSet<String>(currentNames);
            deletedNames.removeAll(uptodateNames);

            for (String name : deletedNames) {
              scheduleDeletion(file.findChild(name));
            }

            for (String name : newNames) {
              boolean isDirectory = delegate.isDirectory(new FakeVirtualFile(file, name));
              scheduleCreation(file, name, isDirectory);
            }

            for (VirtualFile child : file.getChildren()) {
              if (!deletedNames.contains(child.getName())) {
                scheduleChildRefresh(file, child, delegate);
              }
            }
          }
          else {
            for (VirtualFile child : file.getCachedChildren()) {
              if (delegate.exists(child)) {
                scheduleChildRefresh(file, child, delegate);
              }
              else {
                scheduleDeletion(child);
              }
            }

            final List<String> names = dir.getSuspicousNames();
            for (String name : names) {
              if (name.length() == 0) continue;

              final VirtualFile fake = new FakeVirtualFile(file, name);
              if (delegate.exists(fake)) {
                scheduleCreation(file, name, delegate.isDirectory(fake));
              }
            }
          }
        }
        else {
          long currentTimestamp = persistence.getTimeStamp(file);
          long updtodateTimestamp = delegate.getTimeStamp(file);

          if (currentTimestamp != updtodateTimestamp) {
            scheduleUpdateContent(file);
          }
        }

        boolean currentWritable = persistence.isWritable(file);
        boolean uptodateWritable = delegate.isWritable(file);

        if (currentWritable != uptodateWritable) {
          scheduleWritableAttributeChange(file, currentWritable, uptodateWritable);
        }

        file.markClean();
      }
    }
  }

  private void scheduleChildRefresh(final VirtualFileSystemEntry file, final VirtualFile child, final NewVirtualFileSystem delegate) {
    final boolean currentIsDirectory = child.isDirectory();
    final boolean uptodateisDirectory = delegate.isDirectory(child);
    if (currentIsDirectory != uptodateisDirectory) {
      scheduleDeletion(child);
      scheduleCreation(file, child.getName(), uptodateisDirectory);
    }
    else if (myIsRecursive || !currentIsDirectory) {
      myRefreshQueue.addLast(child);
    }
  }

  private void scheduleWritableAttributeChange(final VirtualFileSystemEntry file,
                                               final boolean currentWritable,
                                               final boolean uptodateWritable) {
    myEvents.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_WRITABLE, currentWritable, uptodateWritable, true));
  }

  private void scheduleUpdateContent(final VirtualFileSystemEntry file) {
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(final VirtualFileSystemEntry parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true));
  }

  private void scheduleDeletion(final VirtualFile file) {
    if (file == null) return;
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}
