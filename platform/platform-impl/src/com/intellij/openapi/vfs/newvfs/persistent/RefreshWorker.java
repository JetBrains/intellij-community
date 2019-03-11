// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.containers.Queue;
import com.intellij.util.text.FilePathHashingStrategy;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.openapi.vfs.newvfs.persistent.VfsEventGenerationHelper.LOG;
import static com.intellij.util.containers.ContainerUtil.newTroveSet;

/**
 * @author max
 */
public class RefreshWorker {
  private final boolean myIsRecursive;
  private final Queue<Pair<NewVirtualFile, FileAttributes>> myRefreshQueue = new Queue<>(100);
  private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
  private volatile boolean myCancelled;
  private final LocalFileSystemRefreshWorker myLocalFileSystemRefreshWorker;

  public RefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    boolean canUseNioRefresher = refreshRoot.isInLocalFileSystem() && !(refreshRoot.getFileSystem() instanceof TempFileSystem);
    myLocalFileSystemRefreshWorker = canUseNioRefresher && Registry.is("vfs.use.nio-based.local.refresh.worker") ?
                                     new LocalFileSystemRefreshWorker(refreshRoot, isRecursive) : null;
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(pair(refreshRoot, null));
  }

  @NotNull
  public List<VFileEvent> getEvents() {
    if (myLocalFileSystemRefreshWorker != null) return myLocalFileSystemRefreshWorker.getEvents();
    return myHelper.getEvents();
  }

  public void cancel() {
    if (myLocalFileSystemRefreshWorker != null) myLocalFileSystemRefreshWorker.cancel();
    myCancelled = true;
  }

  public void scan() {
    if (myLocalFileSystemRefreshWorker != null) {
      myLocalFileSystemRefreshWorker.scan();
      return;
    }

    NewVirtualFile root = myRefreshQueue.peekFirst().first;
    NewVirtualFileSystem fs = root.getFileSystem();
    if (root.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }
    try {
      processQueue(fs, PersistentFS.getInstance());
    }
    catch (RefreshCancelledException e) {
      LOG.trace("refresh cancelled");
    }
  }

  private void processQueue(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistence) throws RefreshCancelledException {
    TObjectHashingStrategy<String> strategy = FilePathHashingStrategy.create(fs.isCaseSensitive());

    while (!myRefreshQueue.isEmpty()) {
      Pair<NewVirtualFile, FileAttributes> pair = myRefreshQueue.pullFirst();
      NewVirtualFile file = pair.first;
      if (!myHelper.checkDirty(file)) continue;

      checkCancelled(file);

      FileAttributes attributes = pair.second != null ? pair.second : fs.getAttributes(file);
      if (attributes == null) {
        myHelper.scheduleDeletion(file);
        file.markClean();
        continue;
      }

      NewVirtualFile parent = file.getParent();
      if (parent != null && checkAndScheduleFileTypeChange(fs, parent, file, attributes)) {
        // ignore everything else
        file.markClean();
        continue;
      }

      if (file.isDirectory()) {
        boolean fullSync = ((VirtualDirectoryImpl)file).allChildrenLoaded();
        if (fullSync) {
          fullDirRefresh(fs, persistence, strategy, (VirtualDirectoryImpl)file);
        }
        else {
          partialDirRefresh(fs, strategy, (VirtualDirectoryImpl)file);
        }
      }
      else {
        myHelper.checkContentChanged(file, persistence.getTimeStamp(file), attributes.lastModified,
                                     persistence.getLastRecordedLength(file), attributes.length);
      }

      myHelper.checkWritableAttributeChange(file, persistence.isWritable(file), attributes.isWritable());

      if (SystemInfo.isWindows) {
        myHelper.checkHiddenAttributeChange(file, file.is(VFileProperty.HIDDEN), attributes.isHidden());
      }

      if (attributes.isSymLink()) {
        myHelper.checkSymbolicLinkChange(file, file.getCanonicalPath(), fs.resolveSymLink(file));
      }

      if (myIsRecursive || !file.isDirectory()) {
        file.markClean();
      }
    }
  }

  private void fullDirRefresh(@NotNull NewVirtualFileSystem fs,
                              @NotNull PersistentFS persistence,
                              @NotNull TObjectHashingStrategy<String> strategy,
                              @NotNull VirtualDirectoryImpl dir) {
    while (true) {
      // obtaining directory snapshot
      Pair<String[], VirtualFile[]> result = LocalFileSystemRefreshWorker.getDirectorySnapshot(persistence, dir);
      if (result == null) return;
      String[] persistedNames = result.getFirst();
      VirtualFile[] children = result.getSecond();

      // reading children attributes
      String[] upToDateNames = VfsUtil.filterNames(fs.list(dir));
      Set<String> newNames = newTroveSet(strategy, upToDateNames);
      if (dir.allChildrenLoaded() && children.length < upToDateNames.length) {
        for (VirtualFile child : children) {
          newNames.remove(child.getName());
        }
      }
      else {
        ContainerUtil.removeAll(newNames, persistedNames);
      }

      Set<String> deletedNames = newTroveSet(strategy, persistedNames);
      ContainerUtil.removeAll(deletedNames, upToDateNames);

      OpenTHashSet<String> actualNames = fs.isCaseSensitive() ? null : new OpenTHashSet<>(strategy, upToDateNames);
      if (LOG.isTraceEnabled()) LOG.trace("current=" + Arrays.toString(persistedNames) + " +" + newNames + " -" + deletedNames);

      List<ChildInfo> newKids = new ArrayList<>(newNames.size());
      for (String newName : newNames) {
        checkCancelled(dir);
        ChildInfo record = childRecord(fs, dir, newName);
        if (record != null) {
          newKids.add(record);
        }
        else {
          if (LOG.isTraceEnabled()) LOG.trace("[+] fs=" + fs + " dir=" + dir + " name=" + newName);
        }
      }

      List<Pair<VirtualFile, FileAttributes>> updatedMap = new ArrayList<>(children.length);
      for (VirtualFile child : children) {
        checkCancelled(dir);
        if (deletedNames.contains(child.getName())) continue;
        updatedMap.add(pair(child, fs.getAttributes(child)));
      }

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (!Arrays.equals(persistedNames, persistence.list(dir)) || !Arrays.equals(children, dir.getChildren())) {
          if (LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
          return false;
        }

        for (String name : deletedNames) {
          VirtualFileSystemEntry child = dir.findChild(name);
          if (child != null) {
            myHelper.scheduleDeletion(child);
          }
        }

        for (ChildInfo record : newKids) {
          myHelper.scheduleCreation(dir, record.name, record.attributes, record.symLinkTarget);
        }

        for (Pair<VirtualFile, FileAttributes> pair : updatedMap) {
          VirtualFile child = pair.first;
          FileAttributes childAttributes = pair.second;
          if (childAttributes != null) {
            checkAndScheduleChildRefresh(fs, dir, child, childAttributes);
            checkAndScheduleFileNameChange(actualNames, child);
          }
          else {
            if (LOG.isTraceEnabled()) LOG.warn("[x] fs=" + fs + " dir=" + dir + " name=" + child.getName());
            myHelper.scheduleDeletion(child);
          }
        }

        return true;
      });

      if (hasEvents) break;
    }
  }

  private void partialDirRefresh(@NotNull NewVirtualFileSystem fs,
                                 @NotNull TObjectHashingStrategy<String> strategy,
                                 @NotNull VirtualDirectoryImpl dir) {
    while (true) {
      // obtaining directory snapshot
      Pair<List<VirtualFile>, List<String>> result =
        ReadAction.compute(() -> pair(dir.getCachedChildren(), dir.getSuspiciousNames()));

      List<VirtualFile> cached = result.getFirst();
      List<String> wanted = result.getSecond();

      OpenTHashSet<String> actualNames =
        fs.isCaseSensitive() || cached.isEmpty() ? null : new OpenTHashSet<>(strategy, VfsUtil.filterNames(fs.list(dir)));

      if (LOG.isTraceEnabled()) {
        LOG.trace("cached=" + cached + " actual=" + actualNames);
        LOG.trace("suspicious=" + wanted);
      }

      // reading children attributes
      List<Pair<VirtualFile, FileAttributes>> existingMap = new ArrayList<>(cached.size());
      for (VirtualFile child : cached) {
        checkCancelled(dir);
        existingMap.add(pair(child, fs.getAttributes(child)));
      }

      List<ChildInfo> newKids = new ArrayList<>(wanted.size());
      for (String name : wanted) {
        if (name.isEmpty()) continue;
        checkCancelled(dir);
        ChildInfo record = childRecord(fs, dir, name);
        if (record != null) {
          newKids.add(record);
        }
      }

      // generating events unless a directory was changed in between
      boolean hasEvents = ReadAction.compute(() -> {
        if (!cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames())) {
          if (LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
          return false;
        }

        for (Pair<VirtualFile, FileAttributes> pair : existingMap) {
          VirtualFile child = pair.first;
          FileAttributes childAttributes = pair.second;
          if (childAttributes != null) {
            checkAndScheduleChildRefresh(fs, dir, child, childAttributes);
            checkAndScheduleFileNameChange(actualNames, child);
          }
          else {
            myHelper.scheduleDeletion(child);
          }
        }

        for (ChildInfo record : newKids) {
          myHelper.scheduleCreation(dir, record.name, record.attributes, record.symLinkTarget);
        }

        return true;
      });

      if (hasEvents) break;
    }
  }

  private static ChildInfo childRecord(@NotNull NewVirtualFileSystem fs, @NotNull VirtualFile dir, @NotNull String name) {
    FakeVirtualFile file = new FakeVirtualFile(dir, name);
    FileAttributes attributes = fs.getAttributes(file);
    if (attributes == null) return null;
    boolean isEmptyDir = attributes.isDirectory() && !fs.hasChildren(file);
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(file) : null;
    return new ChildInfo(-1, name, attributes, isEmptyDir ? ChildInfo.EMPTY_ARRAY : null, symlinkTarget);
  }

  private static class RefreshCancelledException extends RuntimeException { }

  private void checkCancelled(@NotNull NewVirtualFile stopAt) {
    if (myCancelled || ourCancellingCondition != null && ourCancellingCondition.fun(stopAt)) {
      if (LOG.isTraceEnabled()) LOG.trace("cancelled at: " + stopAt);
      forceMarkDirty(stopAt);
      while (!myRefreshQueue.isEmpty()) {
        NewVirtualFile next = myRefreshQueue.pullFirst().first;
        forceMarkDirty(next);
      }
      throw new RefreshCancelledException();
    }
  }

  private static void forceMarkDirty(@NotNull NewVirtualFile file) {
    file.markClean();  // otherwise consequent markDirty() won't have any effect
    file.markDirty();
  }

  private void checkAndScheduleChildRefresh(NewVirtualFileSystem fs,
                                            @NotNull VirtualFile parent,
                                            @NotNull VirtualFile child,
                                            @NotNull FileAttributes childAttributes) {
    if (!checkAndScheduleFileTypeChange(fs, parent, child, childAttributes)) {
      boolean upToDateIsDirectory = childAttributes.isDirectory();
      if (myIsRecursive || !upToDateIsDirectory) {
        myRefreshQueue.addLast(pair((NewVirtualFile)child, childAttributes));
      }
    }
  }

  private boolean checkAndScheduleFileTypeChange(NewVirtualFileSystem fs,
                                                 @NotNull VirtualFile parent,
                                                 @NotNull VirtualFile child,
                                                 @NotNull FileAttributes childAttributes) {
    boolean currentIsDirectory = child.isDirectory();
    boolean currentIsSymlink = child.is(VFileProperty.SYMLINK);
    boolean currentIsSpecial = child.is(VFileProperty.SPECIAL);
    boolean upToDateIsDirectory = childAttributes.isDirectory();
    boolean upToDateIsSymlink = childAttributes.isSymLink();
    boolean upToDateIsSpecial = childAttributes.isSpecial();

    if (currentIsDirectory != upToDateIsDirectory || currentIsSymlink != upToDateIsSymlink || currentIsSpecial != upToDateIsSpecial) {
      myHelper.scheduleDeletion(child);
      String symlinkTarget = upToDateIsSymlink ? fs.resolveSymLink(child) : null;
      myHelper.scheduleCreation(parent, child.getName(), childAttributes, symlinkTarget);
      return true;
    }

    return false;
  }

  private void checkAndScheduleFileNameChange(@Nullable OpenTHashSet<String> actualNames, @NotNull VirtualFile child) {
    if (actualNames != null) {
      String currentName = child.getName();
      String actualName = actualNames.get(currentName);
      if (actualName != null && !currentName.equals(actualName)) {
        myHelper.scheduleAttributeChange(child, VirtualFile.PROP_NAME, currentName, actualName);
      }
    }
  }

  private static Function<? super VirtualFile, Boolean> ourCancellingCondition;

  @TestOnly
  public static void setCancellingCondition(@Nullable Function<? super VirtualFile, Boolean> condition) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    LocalFileSystemRefreshWorker.setCancellingCondition(condition);
    ourCancellingCondition = condition;
  }
}