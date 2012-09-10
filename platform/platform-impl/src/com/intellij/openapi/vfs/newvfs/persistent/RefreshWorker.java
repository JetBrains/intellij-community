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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileAttributes;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class RefreshWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker");

  private final boolean myIsRecursive;
  private final Queue<VirtualFile> myRefreshQueue = new Queue<VirtualFile>(100);
  private final List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  public RefreshWorker(final VirtualFile refreshRoot, final boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(refreshRoot);
  }

  public void scan() {
    final NewVirtualFile root = (NewVirtualFile)myRefreshQueue.peekFirst();
    if (!root.isDirty()) return;

    NewVirtualFileSystem fs = root.getFileSystem();
    final FileAttributes rootAttributes = fs.getAttributes(root);

    if (rootAttributes == null) {
      scheduleDeletion(root);
      root.markClean();
      return;
    }

    if (rootAttributes != null && rootAttributes.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }

    final PersistentFS persistence = (PersistentFS)ManagingFS.getInstance();

    while (!myRefreshQueue.isEmpty()) {
      final VirtualFileSystemEntry file = (VirtualFileSystemEntry)myRefreshQueue.pullFirst();
      if (!file.isDirty()) continue;

      final FileAttributes attributes = Comparing.equal(file, root) ? rootAttributes : fs.getAttributes(file);
      if (attributes == null) {
        scheduleDeletion(file);
        continue;
      }

      boolean checkFurther = true;
      final VirtualFileSystemEntry parent = file.getParent();
      if (parent != null && checkAndScheduleAttributesChange(parent, file, attributes)) {
        // ignore everything else
        checkFurther = false;
      }
      else if (file.isDirectory()) {
        final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)file;
        final boolean fullSync = dir.allChildrenLoaded();
        if (fullSync) {
          final Set<String> currentNames = new HashSet<String>(Arrays.asList(persistence.list(file)));
          final Set<String> upToDateNames = new HashSet<String>(Arrays.asList(VfsUtil.filterNames(fs.list(file))));

          final Set<String> newNames = new HashSet<String>(upToDateNames);
          newNames.removeAll(currentNames);

          final Set<String> deletedNames = new HashSet<String>(currentNames);
          deletedNames.removeAll(upToDateNames);

          for (String name : deletedNames) {
            scheduleDeletion(file.findChild(name));
          }

          for (String name : newNames) {
            final FileAttributes childAttributes = fs.getAttributes(new FakeVirtualFile(file, name));
            if (childAttributes != null) {
              scheduleCreation(file, name, childAttributes.isDirectory());
            }
            else {
              LOG.warn("fs=" + fs + " dir=" + file + " name=" + name);
            }
          }

          for (VirtualFile child : file.getChildren()) {
            if (!deletedNames.contains(child.getName())) {
              final FileAttributes childAttributes = fs.getAttributes(child);
              if (childAttributes != null) {
                checkAndScheduleChildRefresh(file, child, childAttributes);
              }
              else {
                LOG.warn("fs=" + fs + " dir=" + file + " name=" + child.getName());
                scheduleDeletion(child);
              }
            }
          }
        }
        else {
          for (VirtualFile child : file.getCachedChildren()) {
            final FileAttributes childAttributes = fs.getAttributes(child);
            if (childAttributes != null) {
              checkAndScheduleChildRefresh(file, child, childAttributes);
            }
            else {
              scheduleDeletion(child);
            }
          }

          final List<String> names = dir.getSuspiciousNames();
          for (String name : names) {
            if (name.isEmpty()) continue;

            final VirtualFile fake = new FakeVirtualFile(file, name);
            final FileAttributes childAttributes = fs.getAttributes(fake);
            if (childAttributes != null) {
              scheduleCreation(file, name, childAttributes.isDirectory());
            }
          }
        }
      }
      else {
        final long currentTimestamp = persistence.getTimeStamp(file);
        final long upToDateTimestamp = attributes.lastModified;
        final long currentLength = persistence.getLength(file);
        final long upToDateLength = attributes.length;

        if (currentTimestamp != upToDateTimestamp || currentLength != upToDateLength) {
          scheduleUpdateContent(file);
        }
      }

      if (checkFurther) {
        final boolean currentWritable = persistence.isWritable(file);
        final boolean upToDateWritable = attributes.isWritable();

        if (currentWritable != upToDateWritable) {
          scheduleWritableAttributeChange(file, currentWritable, upToDateWritable);
        }
      }

      file.markClean();
    }
  }

  private void checkAndScheduleChildRefresh(@NotNull VirtualFileSystemEntry parent,
                                            @NotNull VirtualFile child,
                                            @NotNull FileAttributes childAttributes) {
    if (!checkAndScheduleAttributesChange(parent, child, childAttributes)) {
      final boolean upToDateIsDirectory = childAttributes.isDirectory();
      if (myIsRecursive || !upToDateIsDirectory) {
        myRefreshQueue.addLast(child);
      }
    }
  }

  private boolean checkAndScheduleAttributesChange(@NotNull VirtualFileSystemEntry parent,
                                                   @NotNull VirtualFile child,
                                                   @NotNull FileAttributes childAttributes) {
    final boolean currentIsDirectory = child.isDirectory();
    final boolean currentIsSymlink = child.isSymLink();
    final boolean currentIsSpecial = child.isSpecialFile();
    final boolean upToDateIsDirectory = childAttributes.isDirectory();
    final boolean upToDateIsSymlink = childAttributes.isSymLink();
    final boolean upToDateIsSpecial = child.isSpecialFile();

    if (currentIsDirectory != upToDateIsDirectory ||
        currentIsSymlink != upToDateIsSymlink ||
        currentIsSpecial != upToDateIsSpecial) {
      scheduleDeletion(child);
      scheduleReCreation(parent, child.getName(), upToDateIsDirectory);
      return true;
    }
    else {
      return false;
    }
  }

  private void scheduleWritableAttributeChange(@NotNull VirtualFileSystemEntry file, boolean currentWritable, boolean upToDateWritable) {
    myEvents.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_WRITABLE, currentWritable, upToDateWritable, true));
  }

  private void scheduleUpdateContent(@NotNull VirtualFileSystemEntry file) {
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(@NotNull VirtualFileSystemEntry parent, @NotNull String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, false));
  }

  private void scheduleReCreation(@NotNull VirtualFileSystemEntry parent, @NotNull String childName, final boolean isDirectory) {
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, true));
  }

  private void scheduleDeletion(final VirtualFile file) {
    if (file == null) return;
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}
