/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.Queue;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class LocalFileSystemRefreshWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker");
  private static final Logger LOG_ATTRIBUTES = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker_Attributes");

  private final boolean myIsRecursive;
  private final Queue<NewVirtualFile> myRefreshQueue = new Queue<>(100);
  private final List<VFileEvent> myFileEventSet = new ArrayList<>();
  private volatile boolean myCancelled;

  LocalFileSystemRefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(refreshRoot);
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    return myFileEventSet;
  }

  public void cancel() {
    myCancelled = true;
  }

  public void scan() {
    NewVirtualFile root = myRefreshQueue.pullFirst();
    boolean rootDirty = root.isDirty();
    if (LOG.isDebugEnabled()) LOG.debug("root=" + root + " dirty=" + rootDirty);
    if (!rootDirty) return;

    NewVirtualFileSystem fs = root.getFileSystem();
    FileAttributes rootAttributes = fs.getAttributes(root);
    if (rootAttributes == null) {
      addDeletionEventTo(root, myFileEventSet);
      root.markClean();
      return;
    }
    if (rootAttributes.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }

    myRefreshQueue.addLast(root);

    try {
      processQueue(fs, PersistentFS.getInstance());
    }
    catch (RefreshCancelledException e) {
      LOG.debug("refresh cancelled");
    }
  }

  private void processQueue(NewVirtualFileSystem fs, PersistentFS persistence) throws RefreshCancelledException {
    TObjectHashingStrategy<String> strategy = FilePathHashingStrategy.create(fs.isCaseSensitive());

    while (!myRefreshQueue.isEmpty()) {
      NewVirtualFile file = myRefreshQueue.pullFirst();
      boolean fileDirty = file.isDirty();
      if (LOG.isTraceEnabled()) LOG.trace("file=" + file + " dirty=" + fileDirty);
      if (!fileDirty) continue;

      checkCancelled(file);

      if (file.isDirectory()) {
        boolean fullSync = ((VirtualDirectoryImpl)file).allChildrenLoaded();
        if (fullSync) {
          fullDirRefresh(fs, persistence, strategy, (VirtualDirectoryImpl)file);
        }
        else {
          partialDirRefresh(fs, persistence, strategy, (VirtualDirectoryImpl)file);
        }
      }
      else {
        refreshFile(fs, persistence, strategy, file);
      }

      if (myIsRecursive || !file.isDirectory()) {
        file.markClean();
      }
    }
  }

  private void refreshFile(NewVirtualFileSystem fs,
                           PersistentFS persistence,
                           TObjectHashingStrategy<String> strategy,
                           NewVirtualFile file) {
    RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(file, persistence, fs,
                                                                            null,
                                                                            Collections.singletonList(file), strategy);

    refreshingFileVisitor.visit(file);
    myFileEventSet.addAll(refreshingFileVisitor.getEventSet());
  }

  private static final AtomicInteger myRequests = new AtomicInteger();
  private static final AtomicLong myTime = new AtomicLong();

  private void fullDirRefresh(NewVirtualFileSystem fs,
                              PersistentFS persistence,
                              TObjectHashingStrategy<String> strategy,
                              VirtualDirectoryImpl dir) {
    while (true) {
      // obtaining directory snapshot
      Pair<String[], VirtualFile[]> result = getDirectorySnapshot(persistence, dir);
      if (result == null) return;
      String[] currentNames = result.getFirst();
      VirtualFile[] children = result.getSecond();

      RefreshingFileVisitor refreshingFileVisitor =
        new RefreshingFileVisitor(dir, persistence, fs, null, Arrays.asList(children), strategy);

      refreshingFileVisitor.visit(dir);

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (ApplicationManager.getApplication().isDisposed()) {
          return true;
        }
        if (!Arrays.equals(currentNames, persistence.list(dir)) || !Arrays.equals(children, dir.getChildren())) {
          if (LOG.isDebugEnabled()) LOG.debug("retry: " + dir);
          return false;
        }

        myFileEventSet.addAll(refreshingFileVisitor.getEventSet());
        return true;
      });
      if (hasEvents) {
        break;
      }
    }
  }

  static Pair<String[], VirtualFile[]> getDirectorySnapshot(PersistentFS persistence, VirtualDirectoryImpl dir) {
    return ReadAction.compute(() -> {
          if (ApplicationManager.getApplication().isDisposed()) {
            return null;
          }
          return Pair.create(persistence.list(dir), dir.getChildren());
        });
  }

  private void partialDirRefresh(NewVirtualFileSystem fs,
                                 PersistentFS persistence,
                                 TObjectHashingStrategy<String> strategy,
                                 VirtualDirectoryImpl dir) {
    while (true) {
      // obtaining directory snapshot
      Pair<List<VirtualFile>, List<String>> result =
        ReadAction.compute(() -> Pair.create(dir.getCachedChildren(), dir.getSuspiciousNames()));

      List<VirtualFile> cached = result.getFirst();
      List<String> wanted = result.getSecond();

      if (cached.isEmpty() && wanted.isEmpty()) return;
      RefreshingFileVisitor refreshingFileVisitor = new RefreshingFileVisitor(dir, persistence, fs, wanted, cached, strategy);
      refreshingFileVisitor.visit(dir);

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (!cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames())) {
          if (LOG.isDebugEnabled()) LOG.debug("retry: " + dir);
          return false;
        }

        myFileEventSet.addAll(refreshingFileVisitor.getEventSet());

        return true;
      });
      if (hasEvents) {
        break;
      }
    }
  }

  private static class RefreshCancelledException extends RuntimeException {
  }

  private void checkCancelled(@NotNull NewVirtualFile stopAt) {
    if (myCancelled || ourCancellingCondition != null && ourCancellingCondition.fun(stopAt)) {
      forceMarkDirty(stopAt);
      while (!myRefreshQueue.isEmpty()) {
        NewVirtualFile next = myRefreshQueue.pullFirst();
        forceMarkDirty(next);
      }
      throw new RefreshCancelledException();
    }
  }

  private static void forceMarkDirty(NewVirtualFile file) {
    file.markClean();  // otherwise consequent markDirty() won't have any effect
    file.markDirty();
  }

  private static Function<VirtualFile, Boolean> ourCancellingCondition;

  @TestOnly
  static void setCancellingCondition(@Nullable Function<VirtualFile, Boolean> condition) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ourCancellingCondition = condition;
  }

  private static void addDeletionEventTo(@NotNull VirtualFile file, @NotNull Collection<VFileEvent> myFileEvents) {
    if (LOG.isTraceEnabled()) LOG.trace("delete file=" + file);
    myFileEvents.add(new VFileDeleteEvent(null, file, true));
  }

  private class RefreshingFileVisitor extends SimpleFileVisitor<Path> {
    private final List<VFileEvent> myFileEvents = new ArrayList<>();
    private final Map<String, VirtualFile> myPersistentChildren;
    private final Set<String> myChildrenWeAreInterested; // null - no limit

    private final VirtualFile myFileOrDir;
    private final PersistentFS myPersistence;
    private final NewVirtualFileSystem myFs;

    RefreshingFileVisitor(VirtualFile fileOrDir,
                          PersistentFS persistence,
                          NewVirtualFileSystem fs,
                          Collection<String> persistentChildrenToRefresh,
                          Collection<VirtualFile> existingPersistentChildren,
                          TObjectHashingStrategy<String> strategy) {
      myFileOrDir = fileOrDir;
      myPersistence = persistence;
      myFs = fs;
      myPersistentChildren = new THashMap<>(existingPersistentChildren.size(), strategy);
      myChildrenWeAreInterested = persistentChildrenToRefresh != null ? new THashSet<>(persistentChildrenToRefresh, strategy) : null;

      for (VirtualFile child : existingPersistentChildren) {
        String name = child.getName();
        myPersistentChildren.put(name, child);
        if (myChildrenWeAreInterested != null) myChildrenWeAreInterested.add(name);
      }
    }

    private void addAttributeChangeEvent(@NotNull VirtualFile file,
                                         @NotNull @VirtualFile.PropName String property,
                                         Object current,
                                         Object upToDate) {
      if (LOG.isTraceEnabled()) LOG.trace("update '" + property + "' file=" + file);
      myFileEvents.add(new VFilePropertyChangeEvent(null, file, property, current, upToDate, true));
    }

    private void addUpdateContentEvent(@NotNull VirtualFile file) {
      if (LOG.isTraceEnabled()) LOG.trace("update file=" + file);
      myFileEvents.add(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
    }

    private void addCreationEvent(@NotNull VirtualFile parent, @NotNull String childName, boolean isDirectory) {
      if (LOG.isTraceEnabled()) LOG.trace("create parent=" + parent + " name=" + childName + " dir=" + isDirectory);
      myFileEvents.add(new VFileCreateEvent(null, parent, childName, isDirectory, true));
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      String name = file.getName(file.getNameCount() - 1).toString();

      if (acceptsFileName(name)) {
        NewVirtualFile child = (NewVirtualFile)myPersistentChildren.remove(name);
        if (child == null) { // new file is created
          addCreationEvent(myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent(), name, attrs.isDirectory());
          return FileVisitResult.CONTINUE;
        }

        checkCancelled(child);

        boolean currentIsDirectory = child.isDirectory();
        boolean currentIsSymlink = child.is(VFileProperty.SYMLINK);
        boolean currentIsSpecial = child.is(VFileProperty.SPECIAL);

        if (currentIsDirectory != attrs.isDirectory() ||
            currentIsSymlink != attrs.isSymbolicLink() ||
            currentIsSpecial != attrs.isOther()) { // symlink or directory or special changed
          addDeletionEventTo(child, myFileEvents);
          addCreationEvent(myFileOrDir.isDirectory() ? myFileOrDir : myFileOrDir.getParent(), child.getName(), attrs.isDirectory());
          // ignore everything else
          child.markClean();
          return FileVisitResult.CONTINUE;
        }

        String currentName = child.getName();
        if (!currentName.equals(name)) {
          addAttributeChangeEvent(child, VirtualFile.PROP_NAME, currentName, name);
        }

        if (!attrs.isDirectory()) {
          if (myPersistence.getTimeStamp(child) != attrs.lastModifiedTime().toMillis() ||
              myPersistence.getLastRecordedLength(child) != attrs.size()) {
            addUpdateContentEvent(child);
            child.markClean();
            return FileVisitResult.CONTINUE;
          }
        }
        else {
          if (myIsRecursive) {
            myRefreshQueue.addLast(child);
          }
        }

        boolean currentWritable = myPersistence.isWritable(child);
        boolean isWritable;

        if (attrs instanceof DosFileAttributes) {
          DosFileAttributes dosFileAttributes = (DosFileAttributes)attrs;
          isWritable = attrs.isDirectory() || !dosFileAttributes.isReadOnly();
        }
        else if (attrs instanceof PosixFileAttributes) {
          isWritable = ((PosixFileAttributes)attrs).permissions().contains(PosixFilePermission.OWNER_WRITE);
        }
        else {
          isWritable = file.toFile().canWrite();
        }

        if (LOG_ATTRIBUTES.isDebugEnabled()) {
          LOG_ATTRIBUTES
            .debug("file=" + file + " writable vfs=" + child.isWritable() + " persistence=" + currentWritable + " real=" + isWritable);
        }
        if (currentWritable != isWritable) {
          addAttributeChangeEvent(child, VirtualFile.PROP_WRITABLE, currentWritable, isWritable);
        }

        if (attrs instanceof DosFileAttributes) {
          boolean currentHidden = child.is(VFileProperty.HIDDEN);
          boolean upToDateHidden = ((DosFileAttributes)attrs).isHidden();
          if (currentHidden != upToDateHidden) {
            addAttributeChangeEvent(child, VirtualFile.PROP_HIDDEN, currentHidden, upToDateHidden);
          }
        }

        if (attrs.isSymbolicLink()) {
          String currentTarget = child.getCanonicalPath();
          String upToDateTarget = myFs.resolveSymLink(child);
          String upToDateVfsTarget = upToDateTarget != null ? FileUtil.toSystemIndependentName(upToDateTarget) : null;
          if (!Comparing.equal(currentTarget, upToDateVfsTarget)) {
            addAttributeChangeEvent(child, VirtualFile.PROP_SYMLINK_TARGET, currentTarget, upToDateVfsTarget);
          }
        }
        if (!child.isDirectory()) child.markClean();
      }
      return FileVisitResult.CONTINUE;
    }

    boolean acceptsFileName(String name) {
      return !VfsUtil.isBadName(name) && (myChildrenWeAreInterested == null || myChildrenWeAreInterested.contains(name));
    }

    public void visit(VirtualFile fileOrDir) {
      long started = System.nanoTime();

      try {
        Path path = Paths.get(fileOrDir.getPath());
        if (fileOrDir.isDirectory()) {
          Files.walkFileTree(path, EnumSet.noneOf(FileVisitOption.class), 1, this);
        }
        else {
          visitFile(path, Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
        }
      }
      catch (AccessDeniedException ignore) {
      }
      catch (IOException ex) {
        LOG.error(ex);
      }

      int requests = myRequests.incrementAndGet();
      long l = myTime.addAndGet(System.nanoTime() - started);

      if (requests % 1000 == 0) {
        System.out.println("refresh:" + myRequests + " for " + l / 1_000_000 + "ms");
      }
    }

    @NotNull
    List<VFileEvent> getEventSet() {
      if (!myPersistentChildren.isEmpty()) {
        for (VirtualFile child : myPersistentChildren.values()) {
          addDeletionEventTo(child, myFileEvents);
        }
        myPersistentChildren.clear();
      }

      return myFileEvents;
    }
  }
}