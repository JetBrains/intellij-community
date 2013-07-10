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
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
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
  private final Queue<Pair<NewVirtualFile, FileAttributes>> myRefreshQueue = new Queue<Pair<NewVirtualFile, FileAttributes>>(100);
  private final List<VFileEvent> myEvents = new ArrayList<VFileEvent>();
  private volatile boolean myCancelled = false;

  public RefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(Pair.create(refreshRoot, (FileAttributes)null));
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    return myEvents;
  }

  public void cancel() {
    myCancelled = true;
  }

  public void scan() {
    NewVirtualFile root = myRefreshQueue.pullFirst().first;
    boolean rootDirty = root.isDirty();
    debug(LOG, "root=%s dirty=%b", root, rootDirty);
    if (!rootDirty) return;

    NewVirtualFileSystem fs = root.getFileSystem();
    FileAttributes rootAttributes = fs.getAttributes(root);
    if (rootAttributes == null) {
      scheduleDeletion(root);
      root.markClean();
      return;
    }
    if (rootAttributes != null && rootAttributes.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }
    myRefreshQueue.addLast(Pair.create(root, rootAttributes));
    PersistentFS persistence = PersistentFS.getInstance();

    main:
    while (!myRefreshQueue.isEmpty() && !myCancelled) {
      Pair<NewVirtualFile, FileAttributes> pair = myRefreshQueue.pullFirst();
      NewVirtualFile file = pair.first;
      boolean fileDirty = file.isDirty();
      debug(LOG, "file=%s dirty=%b", file, fileDirty);
      if (!fileDirty) continue;

      FileAttributes attributes = pair.second != null ? pair.second : fs.getAttributes(file);
      if (attributes == null) {
        scheduleDeletion(file);
        continue;
      }

      boolean checkFurther = true;
      NewVirtualFile parent = file.getParent();
      if (parent != null &&
          (checkAndScheduleAttributesChange(parent, file, attributes) ||
           checkAndScheduleSymLinkTargetChange(parent, file, attributes, fs))) {
        // ignore everything else
        checkFurther = false;
      }
      else if (file.isDirectory()) {
        VirtualDirectoryImpl dir = (VirtualDirectoryImpl)file;
        boolean fullSync = dir.allChildrenLoaded();
        if (fullSync) {
          Set<String> currentNames = newHashSet(persistence.list(file));
          Set<String> upToDateNames = newHashSet(VfsUtil.filterNames(fs.list(file)));
          Set<String> newNames = newHashSet(upToDateNames);
          newNames.removeAll(currentNames);
          Set<String> deletedNames = newHashSet(currentNames);
          deletedNames.removeAll(upToDateNames);
          debug(LOG, "current=%s +%s -%s", currentNames, newNames, deletedNames);

          for (String name : deletedNames) {
            scheduleDeletion(file.findChild(name));
          }

          for (String name : newNames) {
            if (myCancelled) break main;
            FileAttributes childAttributes = fs.getAttributes(new FakeVirtualFile(file, name));
            if (childAttributes != null) {
              scheduleCreation(file, name, childAttributes.isDirectory());
            }
            else {
              LOG.warn("fs=" + fs + " dir=" + file + " name=" + name);
            }
          }

          for (VirtualFile child : file.getChildren()) {
            if (myCancelled) break main;
            if (!deletedNames.contains(child.getName())) {
              FileAttributes childAttributes = fs.getAttributes(child);
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
          Collection<VirtualFile> cachedChildren = file.getCachedChildren();
          debug(LOG, "cached=%s", cachedChildren);
          for (VirtualFile child : cachedChildren) {
            if (myCancelled) break main;
            FileAttributes childAttributes = fs.getAttributes(child);
            if (childAttributes != null) {
              checkAndScheduleChildRefresh(file, child, childAttributes);
            }
            else {
              scheduleDeletion(child);
            }
          }

          List<String> names = dir.getSuspiciousNames();
          debug(LOG, "suspicious=%s", names);
          for (String name : names) {
            if (myCancelled) break main;
            if (name.isEmpty()) continue;

            VirtualFile fake = new FakeVirtualFile(file, name);
            FileAttributes childAttributes = fs.getAttributes(fake);
            if (childAttributes != null) {
              scheduleCreation(file, name, childAttributes.isDirectory());
            }
          }
        }
      }
      else {
        long currentTimestamp = persistence.getTimeStamp(file);
        long upToDateTimestamp = attributes.lastModified;
        long currentLength = persistence.getLength(file);
        long upToDateLength = attributes.length;

        if (currentTimestamp != upToDateTimestamp || currentLength != upToDateLength) {
          scheduleUpdateContent(file);
        }
      }

      if (checkFurther) {
        boolean currentWritable = persistence.isWritable(file);
        boolean upToDateWritable = attributes.isWritable();
        if (currentWritable != upToDateWritable) {
          scheduleAttributeChange(file, VirtualFile.PROP_WRITABLE, currentWritable, upToDateWritable);
        }

        if (SystemInfo.isWindows) {
          boolean currentHidden = file.is(VFileProperty.HIDDEN);
          boolean upToDateHidden = attributes.isHidden();
          if (currentHidden != upToDateHidden) {
            scheduleAttributeChange(file, VirtualFile.PROP_HIDDEN, currentHidden, upToDateHidden);
          }
        }
      }

      file.markClean();
    }
  }

  private void checkAndScheduleChildRefresh(@NotNull VirtualFile parent,
                                            @NotNull VirtualFile child,
                                            @NotNull FileAttributes childAttributes) {
    if (!checkAndScheduleAttributesChange(parent, child, childAttributes)) {
      boolean upToDateIsDirectory = childAttributes.isDirectory();
      if (myIsRecursive || !upToDateIsDirectory) {
        myRefreshQueue.addLast(Pair.create((NewVirtualFile)child, childAttributes));
      }
    }
  }

  private boolean checkAndScheduleAttributesChange(@NotNull VirtualFile parent,
                                                   @NotNull VirtualFile child,
                                                   @NotNull FileAttributes childAttributes) {
    boolean currentIsDirectory = child.isDirectory();
    boolean currentIsSymlink = child.is(VFileProperty.SYMLINK);
    boolean currentIsSpecial = child.is(VFileProperty.SPECIAL);
    boolean upToDateIsDirectory = childAttributes.isDirectory();
    boolean upToDateIsSymlink = childAttributes.isSymLink();
    boolean upToDateIsSpecial = childAttributes.isSpecial();

    if (currentIsDirectory != upToDateIsDirectory || currentIsSymlink != upToDateIsSymlink || currentIsSpecial != upToDateIsSpecial) {
      scheduleDeletion(child);
      scheduleReCreation(parent, child.getName(), upToDateIsDirectory);
      return true;
    }

    return false;
  }

  private boolean checkAndScheduleSymLinkTargetChange(@NotNull VirtualFile parent,
                                                      @NotNull VirtualFile child,
                                                      @NotNull FileAttributes childAttributes,
                                                      @NotNull NewVirtualFileSystem fs) {
    if (childAttributes.isSymLink()) {
      String currentTarget = child.getCanonicalPath();
      String upToDateTarget = fs.resolveSymLink(child);
      String upToDateVfsTarget = upToDateTarget != null ? FileUtil.toSystemIndependentName(upToDateTarget) : null;

      if (!Comparing.equal(currentTarget, upToDateVfsTarget)) {
        scheduleDeletion(child);
        scheduleReCreation(parent, child.getName(), childAttributes.isDirectory());
        return true;
      }
    }

    return false;
  }

  private void scheduleAttributeChange(@NotNull VirtualFile file, String property, boolean current, boolean upToDate) {
    debug(LOG, "update '%s' file=%s", property, file);
    myEvents.add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
  }

  private void scheduleUpdateContent(@NotNull VirtualFile file) {
    debug(LOG, "update file=%s", file);
    myEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private void scheduleCreation(@NotNull VirtualFile parent, @NotNull String childName, boolean isDirectory) {
    debug(LOG, "create parent=%s name=%s dir=%b", parent, childName, isDirectory);
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, false));
  }

  private void scheduleReCreation(@NotNull VirtualFile parent, @NotNull String childName, boolean isDirectory) {
    debug(LOG, "re-create parent=%s name=%s dir=%b", parent, childName, isDirectory);
    myEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true, true));
  }

  private void scheduleDeletion(@Nullable VirtualFile file) {
    if (file == null) return;
    debug(LOG, "delete file=%s", file);
    myEvents.add(new VFileDeleteEvent(null, file, true));
  }
}
