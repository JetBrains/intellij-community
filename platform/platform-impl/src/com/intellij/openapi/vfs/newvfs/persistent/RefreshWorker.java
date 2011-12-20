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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.Queue;

import java.util.*;

/**
 * @author max
 */
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
      if (root.isDirectory()) {
        delegate = PersistentFS.replaceWithNativeFS(delegate);
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
            Set<String> upToDateNames = new HashSet<String>(Arrays.asList(VfsUtil.filterNames(delegate.list(file))));

            Set<String> newNames = new HashSet<String>(upToDateNames);
            newNames.removeAll(currentNames);

            Set<String> deletedNames = new HashSet<String>(currentNames);
            deletedNames.removeAll(upToDateNames);

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

            final List<String> names = dir.getSuspiciousNames();
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
          long upToDateTimestamp = delegate.getTimeStamp(file);

          if (currentTimestamp != upToDateTimestamp) {
            scheduleUpdateContent(file);
          }
        }

        boolean currentWritable = persistence.isWritable(file);
        boolean upToDateWritable = delegate.isWritable(file);

        if (currentWritable != upToDateWritable) {
          scheduleWritableAttributeChange(file, currentWritable, upToDateWritable);
        }

        file.markClean();
      }
    }
  }

  private void scheduleChildRefresh(final VirtualFileSystemEntry file, final VirtualFile child, final NewVirtualFileSystem delegate) {
    final boolean currentIsDirectory = child.isDirectory();
    final boolean currentIsSymlink = child.isSymLink();
    final boolean currentIsSpecial = child.isSpecialFile();
    final boolean upToDateIsDirectory = delegate.isDirectory(child);
    final boolean upToDateIsSymlink = delegate.isSymLink(child);
    final boolean upToDateIsSpecial = delegate.isSpecialFile(child);
    if (currentIsDirectory != upToDateIsDirectory || currentIsSymlink != upToDateIsSymlink || currentIsSpecial != upToDateIsSpecial) {
      scheduleDeletion(child);
      scheduleReCreation(file, child.getName(), upToDateIsDirectory);
    }
    else if (myIsRecursive || !currentIsDirectory) {
      myRefreshQueue.addLast(child);
    }
  }

  private void scheduleWritableAttributeChange(final VirtualFileSystemEntry file,
                                               final boolean currentWritable,
                                               final boolean upToDateWritable) {
    myEvents.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_WRITABLE, currentWritable, upToDateWritable, true));
  }

  private void scheduleUpdateContent(final VirtualFileSystemEntry file) {
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(final VirtualFileSystemEntry parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, false));
  }

  private void scheduleReCreation(final VirtualFileSystemEntry parent, final String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, true));
  }

  private void scheduleDeletion(final VirtualFile file) {
    if (file == null) return;
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}
