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
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
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
import java.util.function.Consumer;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.openapi.vfs.newvfs.persistent.VfsEventGenerationHelper.LOG;
import static com.intellij.util.containers.ContainerUtil.newTroveSet;

/**
 * @author max
 */
public class RefreshWorker {
  private final boolean myIsRecursive;
  private final Queue<NewVirtualFile> myRefreshQueue = new Queue<>(100);
  private final VfsEventGenerationHelper myHelper = new VfsEventGenerationHelper();
  private volatile boolean myCancelled;
  private final LocalFileSystemRefreshWorker myLocalFileSystemRefreshWorker;

  public RefreshWorker(@NotNull NewVirtualFile refreshRoot, boolean isRecursive) {
    boolean canUseNioRefresher = refreshRoot.isInLocalFileSystem() &&
                                 !(refreshRoot.getFileSystem() instanceof TempFileSystem) &&
                                 Registry.is("vfs.use.nio-based.local.refresh.worker");
    myLocalFileSystemRefreshWorker = canUseNioRefresher ? new LocalFileSystemRefreshWorker(refreshRoot, isRecursive) : null;
    myIsRecursive = isRecursive;
    myRefreshQueue.addLast(refreshRoot);
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

    NewVirtualFile root = myRefreshQueue.pullFirst();
    NewVirtualFileSystem fs = root.getFileSystem();
    if (root.isDirectory()) {
      fs = PersistentFS.replaceWithNativeFS(fs);
    }
    PersistentFS persistence = PersistentFS.getInstance();

    FileAttributes attributes = fs.getAttributes(root);
    if (attributes == null) {
      myHelper.scheduleDeletion(root);
      root.markClean();
      return;
    }

    checkAndScheduleChildRefresh(fs, persistence, root.getParent(), root, attributes);

    if (root.isDirty()) {
      if (myRefreshQueue.isEmpty()) {
        myRefreshQueue.addLast(root);
      }
      try {
        processQueue(fs, persistence);
      }
      catch (RefreshCancelledException e) {
        LOG.trace("refresh cancelled");
      }
    }
  }

  private void processQueue(@NotNull NewVirtualFileSystem fs, @NotNull PersistentFS persistence) throws RefreshCancelledException {
    TObjectHashingStrategy<String> strategy = FilePathHashingStrategy.create(fs.isCaseSensitive());

    while (!myRefreshQueue.isEmpty()) {
      VirtualDirectoryImpl dir = (VirtualDirectoryImpl)myRefreshQueue.pullFirst();
      boolean fullSync = dir.allChildrenLoaded();
      boolean succeeded;
      do {
        myHelper.beginTransaction();
        succeeded = fullSync ? fullDirRefresh(fs, persistence, strategy, dir) : partialDirRefresh(fs, persistence, strategy, dir);
        myHelper.endTransaction(succeeded);
        if (!succeeded && LOG.isTraceEnabled()) LOG.trace("retry: " + dir);
      }
      while (!succeeded);

      if (myIsRecursive) {
        dir.markClean();
      }
    }
  }

  private boolean fullDirRefresh(@NotNull NewVirtualFileSystem fs,
                                 @NotNull PersistentFS persistence,
                                 @NotNull TObjectHashingStrategy<String> strategy,
                                 @NotNull VirtualDirectoryImpl dir) {
    Pair<String[], VirtualFile[]> snapshot = LocalFileSystemRefreshWorker.getDirectorySnapshot(persistence, dir);
    if (snapshot == null) return false;
    String[] persistedNames = snapshot.getFirst();
    VirtualFile[] children = snapshot.getSecond();

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
      if (!deletedNames.contains(child.getName())) {
        updatedMap.add(pair(child, fs.getAttributes(child)));
      }
    }

    if (isDirectoryChanged(persistence, dir, persistedNames, children)) {
      return false;
    }

    for (String name : deletedNames) {
      VirtualFileSystemEntry child = dir.findChild(name);
      if (child != null) {
        myHelper.scheduleDeletion(child);
      }
    }

    for (ChildInfo record : newKids) {
      myHelper.scheduleCreation(dir, record.getName().toString(), record.getFileAttributes(), record.getSymLinkTarget(), () -> checkCancelled(dir));
    }

    for (Pair<VirtualFile, FileAttributes> pair : updatedMap) {
      NewVirtualFile child = (NewVirtualFile)pair.first;
      checkCancelled(child);
      FileAttributes childAttributes = pair.second;
      if (childAttributes != null) {
        checkAndScheduleChildRefresh(fs, persistence, dir, child, childAttributes);
        checkAndScheduleFileNameChange(actualNames, child);
      }
      else {
        if (LOG.isTraceEnabled()) LOG.warn("[x] fs=" + fs + " dir=" + dir + " name=" + child.getName());
        myHelper.scheduleDeletion(child);
      }
    }

    return !isDirectoryChanged(persistence, dir, persistedNames, children);
  }

  private boolean isDirectoryChanged(@NotNull PersistentFS persistence,
                                     @NotNull VirtualDirectoryImpl dir,
                                     @NotNull String[] persistedNames,
                                     @NotNull VirtualFile[] children) {
    return ReadAction.compute(() -> {
      checkCancelled(dir);
      return !Arrays.equals(persistedNames, persistence.list(dir)) || !Arrays.equals(children, dir.getChildren());
    });
  }

  private boolean partialDirRefresh(@NotNull NewVirtualFileSystem fs,
                                    @NotNull PersistentFS persistence,
                                    @NotNull TObjectHashingStrategy<String> strategy,
                                    @NotNull VirtualDirectoryImpl dir) {
    Pair<List<VirtualFile>, List<String>> snapshot = ReadAction.compute(() -> {
      checkCancelled(dir);
      return pair(dir.getCachedChildren(), dir.getSuspiciousNames());
    });
    List<VirtualFile> cached = snapshot.getFirst();
    List<String> wanted = snapshot.getSecond();

    OpenTHashSet<String> actualNames =
      fs.isCaseSensitive() || cached.isEmpty() ? null : new OpenTHashSet<>(strategy, VfsUtil.filterNames(fs.list(dir)));

    if (LOG.isTraceEnabled()) LOG.trace("cached=" + cached + " actual=" + actualNames + " suspicious=" + wanted);

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

    if (isDirectoryChanged(dir, cached, wanted)) {
      return false;
    }

    for (Pair<VirtualFile, FileAttributes> pair : existingMap) {
      NewVirtualFile child = (NewVirtualFile)pair.first;
      checkCancelled(child);
      FileAttributes childAttributes = pair.second;
      if (childAttributes != null) {
        checkAndScheduleChildRefresh(fs, persistence, dir, child, childAttributes);
        checkAndScheduleFileNameChange(actualNames, child);
      }
      else {
        myHelper.scheduleDeletion(child);
      }
    }

    for (ChildInfo record : newKids) {
      myHelper.scheduleCreation(dir, record.getName().toString(), record.getFileAttributes(), record.getSymLinkTarget(), () -> checkCancelled(dir));
    }

    return !isDirectoryChanged(dir, cached, wanted);
  }

  private boolean isDirectoryChanged(@NotNull VirtualDirectoryImpl dir, @NotNull List<VirtualFile> cached, @NotNull List<String> wanted) {
    return ReadAction.compute(() -> {
      checkCancelled(dir);
      return !cached.equals(dir.getCachedChildren()) || !wanted.equals(dir.getSuspiciousNames());
    });
  }

  @Nullable
  private static ChildInfo childRecord(@NotNull NewVirtualFileSystem fs, @NotNull VirtualFile dir, @NotNull String name) {
    FakeVirtualFile file = new FakeVirtualFile(dir, name);
    FileAttributes attributes = fs.getAttributes(file);
    if (attributes == null) return null;
    boolean isEmptyDir = attributes.isDirectory() && !fs.hasChildren(file);
    String symlinkTarget = attributes.isSymLink() ? fs.resolveSymLink(file) : null;
    return new ChildInfoImpl(ChildInfoImpl.UNKNOWN_ID_YET, name, attributes, isEmptyDir ? ChildInfo.EMPTY_ARRAY : null, symlinkTarget);
  }

  static class RefreshCancelledException extends RuntimeException {
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  private void checkCancelled(@NotNull NewVirtualFile stopAt) throws RefreshCancelledException {
    if (ourTestListener != null) {
      ourTestListener.accept(stopAt);
    }
    if (myCancelled) {
      if (LOG.isTraceEnabled()) LOG.trace("cancelled at: " + stopAt);
      forceMarkDirty(stopAt);
      while (!myRefreshQueue.isEmpty()) {
        forceMarkDirty(myRefreshQueue.pullFirst());
      }
      throw new RefreshCancelledException();
    }
  }

  private static void forceMarkDirty(@NotNull NewVirtualFile file) {
    file.markClean();  // otherwise consequent markDirty() won't have any effect
    file.markDirty();
  }

  private void checkAndScheduleChildRefresh(@NotNull NewVirtualFileSystem fs,
                                            @NotNull PersistentFS persistence,
                                            @Nullable NewVirtualFile parent,
                                            @NotNull NewVirtualFile child,
                                            @NotNull FileAttributes childAttributes) {
    if (!VfsEventGenerationHelper.checkDirty(child)) {
      return;
    }

    if (checkAndScheduleFileTypeChange(fs, parent, child, childAttributes)) {
      child.markClean();
      return;
    }

    myHelper.checkWritableAttributeChange(child, persistence.isWritable(child), childAttributes.isWritable());

    if (SystemInfo.isWindows) {
      myHelper.checkHiddenAttributeChange(child, child.is(VFileProperty.HIDDEN), childAttributes.isHidden());
    }

    if (childAttributes.isSymLink()) {
      myHelper.checkSymbolicLinkChange(child, child.getCanonicalPath(), fs.resolveSymLink(child));
    }

    if (!childAttributes.isDirectory()) {
      long oltTS = persistence.getTimeStamp(child);
      long newTS = childAttributes.lastModified;
      long oldLength = persistence.getLastRecordedLength(child);
      long newLength = childAttributes.length;
      myHelper.checkContentChanged(child, oltTS, newTS, oldLength, newLength);

      child.markClean();
    }
    else if (myIsRecursive) {
      myRefreshQueue.addLast(child);
    }
  }

  private boolean checkAndScheduleFileTypeChange(@NotNull NewVirtualFileSystem fs,
                                                 @Nullable NewVirtualFile parent,
                                                 @NotNull NewVirtualFile child,
                                                 @NotNull FileAttributes childAttributes) {
    boolean currentIsDirectory = child.isDirectory();
    boolean currentIsSymlink = child.is(VFileProperty.SYMLINK);
    boolean currentIsSpecial = child.is(VFileProperty.SPECIAL);
    boolean upToDateIsDirectory = childAttributes.isDirectory();
    boolean upToDateIsSymlink = childAttributes.isSymLink();
    boolean upToDateIsSpecial = childAttributes.isSpecial();

    if (currentIsDirectory != upToDateIsDirectory || currentIsSymlink != upToDateIsSymlink || currentIsSpecial != upToDateIsSpecial) {
      myHelper.scheduleDeletion(child);
      assert parent != null : "transgender orphan: " + child + ' ' + childAttributes;
      String symlinkTarget = upToDateIsSymlink ? fs.resolveSymLink(child) : null;
      myHelper.scheduleCreation(parent, child.getName(), childAttributes, symlinkTarget, () -> checkCancelled(parent));
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

  private static Consumer<? super VirtualFile> ourTestListener;

  @TestOnly
  public static void setTestListener(@Nullable Consumer<? super VirtualFile> testListener) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ourTestListener = testListener;
    LocalFileSystemRefreshWorker.setTestListener(testListener);
  }
}