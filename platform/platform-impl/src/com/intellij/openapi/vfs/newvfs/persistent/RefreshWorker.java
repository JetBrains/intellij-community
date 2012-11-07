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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.diagnostic.LogUtil.debug;
import static com.intellij.util.containers.ContainerUtil.newHashSet;

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
    final boolean rootDirty = root.isDirty();
    debug(LOG, "root=%s dirty=%b", root, rootDirty);
    if (!rootDirty) return;
    final long t = System.currentTimeMillis();

    NewVirtualFileSystem fs = root.getFileSystem();
    final FileAttributes rootAttributes = fs.getAttributes(root);

    if (rootAttributes == null) {
      scheduleDeletion(root);
      root.markClean();
      debug(LOG, "root=%s time=%d", root, System.currentTimeMillis() - t);
      return;
    }

    if (rootAttributes != null && rootAttributes.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }

    final PersistentFS persistence = PersistentFS.getInstance();

    while (!myRefreshQueue.isEmpty()) {
      final VirtualFileSystemEntry file = (VirtualFileSystemEntry)myRefreshQueue.pullFirst();
      final boolean fileDirty = file.isDirty();
      debug(LOG, "file=%s dirty=%b", file, fileDirty);
      if (!fileDirty) continue;

      final FileAttributes attributes = Comparing.equal(file, root) ? rootAttributes : fs.getAttributes(file);
      if (attributes == null) {
        scheduleDeletion(file);
        continue;
      }

      boolean checkFurther = true;
      final VirtualFileSystemEntry parent = file.getParent();
      if (parent != null &&
          (checkAndScheduleAttributesChange(parent, file, attributes) ||
           checkAndScheduleSymLinkTargetChange(parent, file, attributes, fs))) {
        // ignore everything else
        checkFurther = false;
      }
      else if (file.isDirectory()) {
        final VirtualDirectoryImpl dir = (VirtualDirectoryImpl)file;
        final boolean fullSync = dir.allChildrenLoaded();
        if (fullSync) {
          final Set<String> currentNames = newHashSet(persistence.list(file));
          final Set<String> upToDateNames = newHashSet(VfsUtil.filterNames(fs.list(file)));
          final Set<String> newNames = newHashSet(upToDateNames);
          newNames.removeAll(currentNames);
          final Set<String> deletedNames = newHashSet(currentNames);
          deletedNames.removeAll(upToDateNames);
          debug(LOG, "current=%s +%s -%s", currentNames, newNames, deletedNames);

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
          final Collection<VirtualFile> cachedChildren = file.getCachedChildren();
          debug(LOG, "cached=%s", cachedChildren);
          for (VirtualFile child : cachedChildren) {
            final FileAttributes childAttributes = fs.getAttributes(child);
            if (childAttributes != null) {
              checkAndScheduleChildRefresh(file, child, childAttributes);
            }
            else {
              scheduleDeletion(child);
            }
          }

          final List<String> names = dir.getSuspiciousNames();
          debug(LOG, "suspicious=%s", names);
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

    debug(LOG, "root=%s time=%d", root, System.currentTimeMillis() - t);
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

  private boolean checkAndScheduleSymLinkTargetChange(@NotNull VirtualFileSystemEntry parent,
                                                      @NotNull VirtualFile child,
                                                      @NotNull FileAttributes childAttributes,
                                                      @NotNull NewVirtualFileSystem fs) {
    if (childAttributes.isSymLink()) {
      final String currentTarget = child.getCanonicalPath();
      final String upToDateTarget = fs.resolveSymLink(child);
      final String upToDateVfsTarget = upToDateTarget != null ? FileUtil.toSystemIndependentName(upToDateTarget) : null;
      if (!Comparing.equal(currentTarget, upToDateVfsTarget)) {
        scheduleDeletion(child);
        scheduleReCreation(parent, child.getName(), childAttributes.isDirectory());
        return true;
      }
    }
    return false;
  }

  private void scheduleWritableAttributeChange(@NotNull VirtualFileSystemEntry file, boolean currentWritable, boolean upToDateWritable) {
    debug(LOG, "update r/w file=%s", file);
    myEvents.add(new VFilePropertyChangeEvent(null, file, VirtualFile.PROP_WRITABLE, currentWritable, upToDateWritable, true));
  }

  private void scheduleUpdateContent(@NotNull VirtualFileSystemEntry file) {
    debug(LOG, "update file=%s", file);
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(@NotNull VirtualFileSystemEntry parent, @NotNull String childName, final boolean isDirectory) {
    debug(LOG, "create parent=%s name=%s dir=%b", parent, childName, isDirectory);
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, false));
  }

  private void scheduleReCreation(@NotNull VirtualFileSystemEntry parent, @NotNull String childName, final boolean isDirectory) {
    debug(LOG, "re-create parent=%s name=%s dir=%b", parent, childName, isDirectory);
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, true));
  }

  private void scheduleDeletion(@Nullable final VirtualFile file) {
    if (file == null) return;
    debug(LOG, "delete file=%s", file);
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    return myEvents;
  }
}
