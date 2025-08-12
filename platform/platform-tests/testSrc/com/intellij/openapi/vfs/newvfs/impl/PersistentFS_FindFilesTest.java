// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.FileNavigator;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.utils.vfs.CheckVFSHealthExtension;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.*;

@TestApplication
@ExtendWith(CheckVFSHealthExtension.class)
public class PersistentFS_FindFilesTest {

  private static final int MAX_FILES_TO_TRIAL = 1_000_000;


  @BeforeAll
  static void createSymlinkToChallengeSymlinkProcessingBranches(@TempDir Path tempDir) throws IOException {
    //Create at least some symlinks in VFS, so their processing could be checked even then test run in isolation, and no one
    // dares create symlinks for us to trouble.
    // Symlinks processing is one of the regular source of issues in VFS, so better be sure we test it over
    Path subdir = tempDir.resolve("a/b/c");
    Files.createDirectories(subdir);

    Path symlinkToSubdir = tempDir.resolve("symlink-to-c");
    Files.createSymbolicLink(symlinkToSubdir, subdir);

    VirtualFileManager vfm = VirtualFileManager.getInstance();
    vfm.refreshAndFindFileByNioPath(tempDir);
    vfm.refreshAndFindFileByNioPath(subdir);
    vfm.refreshAndFindFileByNioPath(symlinkToSubdir);
  }

  @Test
  void findFileById_findsFileForEveryValidFileId_inVFS() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {
      assertEquals(fileId, file.getId(), () -> ".findFileById(" + fileId + ") must return file with same id: " + file);

      NewVirtualFile parent = file.getParent();
      if (parent != null) {//i.e.: not root
        Collection<VirtualFile> cachedChildren = parent.getCachedChildren();
        assertTrue(
          cachedChildren.contains(file),
          () -> "Parent(=" + parent + ") must cache children before it is resolved: cachedChildren: " + cachedChildren + ", child: " + file
        );
      }
    });
  }

  @Test
  void findChild_findsEveryChildByItsOwnName() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {
      assertEquals(fileId, file.getId(), () -> ".findFileById(" + fileId + ") must return file with same id: " + file);

      NewVirtualFile parent = file.getParent();
      if (parent == null) {
        return;//skip roots
      }

      NewVirtualFile child = parent.findChild(file.getName());
      assertEquals(
        child, file,
        "file.parent.findChild( file.name(='" + file.getName() + "') ) must return a file itself"
      );
    });
  }

  @Test
  void findChild_findsEveryChildByItsOwnName_withoutKeepingChildrenByStrongRef() {
    //Here we check soft-ref based cache: contrary to findChild_findsEveryChildByItsOwnName(), we don't load the VirtualFile
    // itself, since that would keep the Segment in memory via strong-ref. Instead, we load children names as String[], and
    // make an attempt to load all the children by their name -- which should sometimes step onto the apt child being evicted
    // from cache due to GC
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {
      if (!file.isDirectory()) {
        return;
      }

      String[] childrenNames = pFS.listPersisted(file);
      for (String childName : childrenNames) {
        NewVirtualFile child = file.findChild(childName);
        assertNotNull(child, () -> "file(" + file + ").findChild(" + childName + ") must return a valid child");
        assertTrue(child.isValid(), () -> "file(" + file + ").findChild(" + childName + ") must return a valid child, but " + child);
        assertEquals(
          child.getName(), childName,
          () -> "child.getName(=" + child.getName() + ") must be '" + childName + "'"
        );
      }
    });
  }

  @Test
  void findFileByPath_findsEveryFileByItsOwnPath() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {

      String path = file.getPath();
      NewVirtualFile fileFoundByPath = VfsImplUtil.findFileByPath(file.getFileSystem(), path);
      assertEquals(
        file,
        fileFoundByPath,
        () -> ".findFileByPath( file.getPath(=" + path + ") ) should resolve to the file itself"
      );
    });
  }

  @Test
  void findCachedFileByPath_findsEveryFileByItsOwnPath() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {

      String path = file.getPath();
      Pair<NewVirtualFile, NewVirtualFile> result = NewVirtualFileSystem.findCachedFileByPath(file.getFileSystem(), path);
      //file is guaranteed to be cached, since we load its path from the VFS:
      NewVirtualFile fileFoundByPath = result.first;
      assertEquals(
        file,
        fileFoundByPath,
        () -> ".findCachedFileByPath( file.getPath(=" + path + ") ) should resolve to the file itself"
      );
    });
  }

  @Test
  void findCachedOrTransientFileByPath_findsEveryFileByItsOwnPath() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {

      String path = file.getPath();
      FileNavigator.NavigateResult<VirtualFile> result = NewVirtualFileSystem.findCachedOrTransientFileByPath(file.getFileSystem(), path);
      assertTrue(result.isResolved(),
                 () -> ".findCachedOrTransientFileByPath( file.getPath(=" + path + ") ) should resolve to the file itself");
      //the file is on the stack, hence, it can't be removed from the cache => [findFileByPathIfCached() == findFileByPath()]
      assertEquals(
        file,
        result.resolvedFileOr(null),
        () -> ".findFileByPathIfCached( file.getPath(=" + path + ") ) should resolve to the file itself"
      );
    });
  }

  @Test
  void findFileByPath_findsEveryFileByItsOwnPath_AdjustedWithDots() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {

      String path = file.getPath();
      String nonCanonicalPath = path.replace("/", "/./"); // [/a] -> [/./a]
      NewVirtualFile fileFoundByNonCanonicalPath = VfsImplUtil.findFileByPath(file.getFileSystem(), nonCanonicalPath);
      assertEquals(
        file,
        fileFoundByNonCanonicalPath,
        () -> ".findFileByPath( file.getPath(!canonical=" + nonCanonicalPath + ") ) should resolve to the file itself"
      );
    });
  }

  @Test
  void findFileByPath_findsEveryFileByItsOwnPath_AdjustedWithDoubleDots() {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();
    int filesToTrial = Math.min(MAX_FILES_TO_TRIAL, pFS.peer().connection().records().maxAllocatedID());
    forRandomValidFilesInVFS(pFS, filesToTrial, (fileId, file) -> {

      NewVirtualFileSystem fileSystem = file.getFileSystem();
      if (!fileSystem.exists(file)) {
        //file was deleted, but VFS is not yet refreshed => path canonicalization works erratically for non-existent files,
        // no reason to deal with it: skip the file
        return;
      }

      if (((VirtualFileSystemEntry)file).thisOrParentHaveSymlink()) {
        //POSIX path resolution rules (which we sort-of follow in .findFileByPath) demands symlinks resolution as soon, as
        // they are met in the path -- which means '/a/../a/' is NOT always equivalent to just '/a/'.
        // Namely, if '/a' is a symlink to -> '/b/c/d' then POSIX (and .findFileByPath) resolves '/a/../a/' to the '/b/c/a',
        // instead of just '/a/' (= '/b/c/d').
        // So _before_ checking [/a/] -> [/a/../a/] equivalent transformation, we ensure no symlinks in the path -- when
        // [/a/] <=> [/a/../a/] equivalency is correct
        return;
      }

      String path = file.getPath();

      String nonCanonicalPath = path.replaceAll("([\\w+\\-.@]+)/", "$1/../$1/");// [/a/] -> [/a/../a/]
      NewVirtualFile fileFoundByNonCanonicalPath = VfsImplUtil.findFileByPath(fileSystem, nonCanonicalPath);
      assertEquals(file, fileFoundByNonCanonicalPath,
                   () -> "[" + nonCanonicalPath + "] must be resolved to it's original [" + path + "]");
    });
  }

  @Test
  void multiThreaded_findChild_findById_GC() throws Exception {
    PersistentFSImpl pFS = (PersistentFSImpl)PersistentFS.getInstance();

    Runnable findByName = () -> forRandomValidFilesInVFS(pFS, MAX_FILES_TO_TRIAL, (fileId, file) -> {
      assertEquals(fileId, file.getId(), () -> ".findFileById(" + fileId + ") must return file with same id: " + file);

      NewVirtualFile parent = file.getParent();
      if (parent == null) {
        return;//skip roots
      }

      NewVirtualFile child = parent.findChild(file.getName());
      assertEquals(child, file, () -> "file.parent.findChild( file.name(='" + file.getName() + "') ) must return a file itself");
    });
    Runnable findByNameWithoutStrongRef = () -> forRandomValidFilesInVFS(pFS, MAX_FILES_TO_TRIAL, (fileId, file) -> {
      if (!file.isDirectory()) {
        return;
      }
      //see findChild_findsEveryChildByItsOwnName_withoutKeepingChildrenByStrongRef() for details
      String[] childrenNames = pFS.listPersisted(file);
      for (String childName : childrenNames) {
        NewVirtualFile child = file.findChild(childName);
        assertNotNull(child, () -> "file(" + file + ").findChild(" + childName + ") must return a valid child");
        assertTrue(child.isValid(), () -> "file(" + file + ").findChild(" + childName + ") must return a valid child, but " + child);
        assertEquals(
          child.getName(), childName,
          () -> "child.getName(=" + child.getName() + ") must be '" + childName + "'"
        );
      }
    });
    Runnable findById = () -> forRandomValidFilesInVFS(pFS, MAX_FILES_TO_TRIAL, (fileId, file) -> {
      assertEquals(fileId, file.getId(), () -> ".findFileById(" + fileId + ") must return file with same id: " + file);

      NewVirtualFile parent = file.getParent();
      if (parent != null) {//i.e.: not root
        Collection<VirtualFile> cachedChildren = parent.getCachedChildren();
        assertTrue(
          cachedChildren.contains(file),
          () -> "Parent(=" + parent + ") must cache children before it is resolved: cachedChildren: " + cachedChildren + ", child: " + file
        );
      }
    });
    Runnable runGc = () -> {
      System.gc();
      try {
        Thread.sleep(100);
      }
      catch (InterruptedException ignored) {
      }
    };

    List<Runnable> tasks = List.of(
      findByName, findByName, findByName,
      findById, findById, findById,
      findByNameWithoutStrongRef, findByNameWithoutStrongRef, findByNameWithoutStrongRef,
      runGc, runGc, runGc
    );

    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      List<? extends Future<?>> futures = ContainerUtil.map(tasks, task -> pool.submit(task));

      for (Future<?> future : futures) {
        future.get();
      }
    }
    finally {
      pool.shutdown();
      //noinspection ResultOfMethodCallIgnored
      pool.awaitTermination(1, MINUTES);
    }
  }


  // ================================ infrastructure ================================================================ //

  private static void forRandomValidFilesInVFS(@NotNull PersistentFSImpl pFS,
                                               int maxFilesToCheck,
                                               @NotNull BiConsumer<Integer, NewVirtualFile> consumer) {
    FSRecordsImpl fsRecords = pFS.peer();
    int maxAllocatedID = fsRecords.connection().records().maxAllocatedID();
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < maxFilesToCheck; i++) {
      //generate fileId randomly, so that all combinations of cached/uncached parents are realised:
      int fileId = rnd.nextInt(FSRecords.MIN_REGULAR_FILE_ID, maxAllocatedID + 1);

      NewVirtualFile file = pFS.findFileById(fileId);
      if (fsRecords.isDeleted(fileId)) {
        assertNull(file, () -> ".findFileById(" + fileId + ") must be null for deleted files");
        continue;
      }
      else if (!rootCanBeLoaded(fileId, pFS, fsRecords)) {
        //root can't be loaded: could be many reasons (i.e. plugin with FileSystem impl was unloaded, missed root, etc)
        // but they don't matter in this test: we don't test roots loading here, but fileId->VirtualFile resolution
        assertNull(file, () -> ".findFileById(" + fileId + ") must be null for orphan files");
        continue;
      }

      assertNotNull(file, () -> ".findFileById(" + fileId + ") must be !null");

      if (file.isValid()) {
        consumer.accept(fileId, file);
      }
    }
  }

  /**
   * @return true if the file root is 'loadable' -- i.e. it has it's FileSystem known to VFS, and other required staff is OK.
   * If the root can't be loaded -- all files under it also invalid for VFS
   */
  private static boolean rootCanBeLoaded(int fileId,
                                         @NotNull PersistentFSImpl pFS,
                                         @NotNull FSRecordsImpl records) {
    while (true) {
      int parentId = records.getParent(fileId);//!isDeleted was already checked
      if (parentId == FSRecords.NULL_FILE_ID) {//==root
        NewVirtualFile root = pFS.findFileById(fileId);
        if (root != null) {
          return true;
        }
        //root can't be loaded by one reason or another -- for this test it doesn't matter
        return false;
      }
      fileId = parentId;
    }
  }
}
