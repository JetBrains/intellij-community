// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises range-list children through VFS operations that require an initialized application in production code paths.
 */
@TestApplication
public class FSRecordsImplRangeListDeletionTest {
  private FSRecordsImpl vfs;
  private int previousRangeListThreshold = -1;

  @Test
  public void recursiveDelete_seesDescendantsStoredThroughRangeList() throws Exception {
    forceRangeListIfMoreChildrenThan(1);
    int parentId = createDirectoryRecord(PersistentFSRecordsStorage.NULL_ID, "parent");
    int childDirectoryId = createDirectoryRecord(parentId, "child-dir");
    int grandChildId = createRecord(childDirectoryId, "grand-child.txt", false);
    saveChildren(parentId, childDirectoryId);
    saveChildren(childDirectoryId, grandChildId);

    deleteRecordRecursively(parentId);

    assertTrue(vfs.isDeleted(parentId), "Recursive delete should mark the root directory itself as deleted");
    assertTrue(vfs.isDeleted(childDirectoryId), "Recursive delete should traverse direct children from the range-list");
    assertTrue(vfs.isDeleted(grandChildId), "Recursive delete should traverse descendants restored from the range-list");
  }

  @BeforeEach
  void setUp(@TempDir Path vfsDir) {
    vfs = FSRecordsImpl.connect(vfsDir, FSRecordsImpl.ON_ERROR_RETHROW);
    previousRangeListThreshold = vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(Integer.MAX_VALUE);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (vfs != null) {
      StorageTestingUtils.bestEffortToCloseAndClean(vfs);
    }
    if (previousRangeListThreshold > 0) {
      vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(previousRangeListThreshold);
    }
    else {
      vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(Integer.MAX_VALUE);
    }
  }

  /** Invokes the recursive deletion operation whose production path must read children through the tree accessor. */
  private void deleteRecordRecursively(int fileId) throws Exception {
    Method method = FSRecordsImpl.class.getDeclaredMethod("deleteRecordRecursively", int.class);
    method.setAccessible(true);
    try {
      method.invoke(vfs, fileId);
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception exception) {
        throw exception;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }

  private void forceRangeListIfMoreChildrenThan(int threshold) {
    previousRangeListThreshold = vfs.treeAccessor().setStoreChildrenAsRangesListIfMoreThan(threshold);
  }

  private int createDirectoryRecord(int parentId,
                                    @NotNull String name) {
    return createRecord(parentId, name, true);
  }

  /** Creates a file record with enough metadata for range-list readers to accept it as a live child. */
  private int createRecord(int parentId,
                           @NotNull String name,
                           boolean directory) {
    int fileId = vfs.createRecord();
    vfs.updateRecordFields(fileId, parentId, attributes(directory), name, true);
    return fileId;
  }

  private static @NotNull FileAttributes attributes(boolean directory) {
    return new FileAttributes(directory, false, false, false, directory ? 0 : 1, 1, true);
  }

  /** Stores children through the normal tree accessor so the test covers writer and reader format choices together. */
  private void saveChildren(int parentId,
                            int... childIds) throws IOException {
    List<ChildInfo> children = new ArrayList<>(childIds.length);
    for (int childId : childIds) {
      children.add(new ChildInfoImpl(childId, vfs.getNameIdByFileId(childId), null, null, null));
    }
    int parentModCount = vfs.getModCount(parentId);
    ListResult listResult = ListResult.allCached(parentModCount, children, parentId);
    vfs.treeAccessor().doSaveChildren(parentId, listResult);
    vfs.setFlags(parentId, vfs.getFlags(parentId) | PersistentFS.Flags.CHILDREN_CACHED);
  }
}
