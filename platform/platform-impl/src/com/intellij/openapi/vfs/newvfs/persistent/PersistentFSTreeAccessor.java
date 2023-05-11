// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER;

class PersistentFSTreeAccessor {
  /**
   * The attribute is a list of child fileId, diff-compressed -- see {@link #doSaveChildren} for details.
   * The FS super-root ({@link #ROOT_RECORD_ID}) is an exceptional case: there is stored both child fileId
   * AND child nameId, both diff-compressed -- see {@link #findOrCreateRootRecord(String)} for details.
   */
  protected static final FileAttribute CHILDREN_ATTR = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");
  /**
   * fileId of super-root, 'root of all roots' record: superficial file record to which all FS roots are
   * attached as children -- see {@link #findOrCreateRootRecord(String)} for details.
   */
  protected static final int ROOT_RECORD_ID = FSRecords.ROOT_FILE_ID;

  protected final PersistentFSAttributeAccessor myAttributeAccessor;
  protected final PersistentFSConnection myFSConnection;
  protected final @Nullable FsRootDataLoader myFsRootDataLoader;
  protected final Lock myRootsAccessLock = new ReentrantLock();

  PersistentFSTreeAccessor(@NotNull PersistentFSAttributeAccessor attributeAccessor,
                           @NotNull PersistentFSConnection connection) {
    myAttributeAccessor = attributeAccessor;
    myFSConnection = connection;
    myFsRootDataLoader = SystemProperties.getBooleanProperty(IDE_USE_FS_ROOTS_DATA_LOADER, false)
                         ? ApplicationManager.getApplication().getService(FsRootDataLoader.class)
                         : null;
  }

  void doSaveChildren(int parentId, @NotNull ListResult toSave) throws IOException {
    myFSConnection.markDirty();
    try (DataOutputStream record = myAttributeAccessor.writeAttribute(parentId, CHILDREN_ATTR)) {
      DataInputOutputUtil.writeINT(record, toSave.children.size());

      int prevId = parentId;
      for (ChildInfo childInfo : toSave.children) {
        final int childId = childInfo.getId();
        if (childId <= 0) {
          throw new IllegalArgumentException("ids must be >0 but got: " + childId + "; childInfo: " + childInfo + "; list: " + toSave);
        }
        if (childId == parentId) {
          FSRecords.LOG.error("Cyclic parent-child relations. parentId=" + parentId + "; list: " + toSave);
        }
        else {
          final int delta = childId - prevId;
          if (prevId != parentId && delta <= 0) {
            throw new IllegalArgumentException("The list must be sorted by (unique) id but got parentId: " +
                                               parentId + "; delta: " + delta + "; childInfo: " + childInfo + "; prevId: " +
                                               prevId + "; toSave: " + toSave);
          }
          DataInputOutputUtil.writeINT(record, delta);
          prevId = childId;
        }
      }
    }
  }

  @NotNull
  ListResult doLoadChildren(final int parentId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);

    final PersistentFSRecordsStorage records = myFSConnection.getRecords();
    try (DataInputStream input = myAttributeAccessor.readAttribute(parentId, CHILDREN_ATTR)) {
      final int count = (input == null) ? 0 : DataInputOutputUtil.readINT(input);
      final List<ChildInfo> children = (count == 0) ? Collections.emptyList() : new ArrayList<>(count);
      int prevId = parentId;
      for (int i = 0; i < count; i++) {
        final int childId = DataInputOutputUtil.readINT(input) + prevId;
        prevId = childId;
        final int nameId = records.getNameId(childId);
        final ChildInfo child = new ChildInfoImpl(childId, nameId, null, null, null);
        children.add(child);
      }
      return new ListResult(children, parentId);
    }
  }

  boolean wereChildrenAccessed(final int fileId) throws IOException {
    return myAttributeAccessor.hasAttributePage(fileId, CHILDREN_ATTR);
  }

  int @NotNull [] listRoots() throws IOException {
    try (DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, CHILDREN_ATTR)) {
      if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;
      final int count = DataInputOutputUtil.readINT(input);
      int[] result = ArrayUtil.newIntArray(count);
      int prevId = 0;
      for (int i = 0; i < count; i++) {
        DataInputOutputUtil.readINT(input); // Name
        prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId; // Id
      }
      return result;
    }
  }

  /**
   * @return array if children fileIds for the given fileId
   * MAYBE rename to childrenIds()?
   */
  int @NotNull [] listIds(final int fileId) throws IOException {
    try (final DataInputStream input = myAttributeAccessor.readAttribute(fileId, CHILDREN_ATTR)) {
      if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;
      final int count = DataInputOutputUtil.readINT(input);
      final int[] result = ArrayUtil.newIntArray(count);
      int prevId = fileId;
      for (int i = 0; i < count; i++) {
        prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId;
      }
      return result;
    }
  }

  boolean mayHaveChildren(final int fileId) throws IOException {
    try (final DataInputStream input = myAttributeAccessor.readAttribute(fileId, CHILDREN_ATTR)) {
      if (input == null) return true;
      final int count = DataInputOutputUtil.readINT(input);
      return count != 0;
    }
  }

  int findOrCreateRootRecord(@NotNull String rootUrl) throws IOException {
    myRootsAccessLock.lock();
    try {
      PersistentFSConnection connection = myFSConnection;

      //TODO RC: with non-strict names enumerator it is possible root==NULL_ID here -> what will happens?
      int rootNameId = connection.getNames().tryEnumerate(rootUrl);

      int[] names = ArrayUtilRt.EMPTY_INT_ARRAY;
      int[] ids = ArrayUtilRt.EMPTY_INT_ARRAY;
      try (final DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, CHILDREN_ATTR)) {
        if (input != null) {
          final int count = DataInputOutputUtil.readINT(input);
          if (count < 0) {
            throw new IOException("ROOT.CHILDREN attribute is corrupted: roots count(=" + count + ") must be >=0");
          }
          names = ArrayUtil.newIntArray(count);
          ids = ArrayUtil.newIntArray(count);
          int prevId = 0;
          int prevNameId = 0;

          for (int i = 0; i < count; i++) {
            final int name = DataInputOutputUtil.readINT(input) + prevNameId;
            final int id = DataInputOutputUtil.readINT(input) + prevId;
            if (name == rootNameId) {
              return id;
            }

            prevNameId = names[i] = name;
            prevId = ids[i] = id;
          }
        }
      }

      connection.markDirty();
      rootNameId = connection.getNames().enumerate(rootUrl);

      try (DataOutputStream output = myAttributeAccessor.writeAttribute(ROOT_RECORD_ID, CHILDREN_ATTR)) {
        final int newRootFileId = FSRecords.createRecord();

        final int index = Arrays.binarySearch(ids, newRootFileId);
        if (index >= 0) {
          throw new AssertionError("Newly allocated newRootFileId(=" + newRootFileId + ") already exists in root record: " +
                                   "ids (=" + Arrays.toString(ids) + "), names(=" + Arrays.toString(names) + "), " +
                                   "rootUrl(=" + rootUrl + "), rootUrlId(=" + rootNameId + ")");
        }
        ids = ArrayUtil.insert(ids, -index - 1, newRootFileId);
        names = ArrayUtil.insert(names, -index - 1, rootNameId);

        saveNameIdSequenceWithDeltas(names, ids, output);
        return newRootFileId;
      }
    }
    finally {
      myRootsAccessLock.unlock();
    }
  }

  void loadDirectoryData(int id, @NotNull VirtualFile parent, @NotNull CharSequence childName, @NotNull NewVirtualFileSystem fs)
    throws IOException {
    if (myFsRootDataLoader != null) {
      myRootsAccessLock.lock();
      try {
        myFsRootDataLoader.loadDirectoryData(getRootsStoragePath(myFsRootDataLoader), id, parent, childName, fs);
      }
      finally {
        myRootsAccessLock.unlock();
      }
    }
  }

  void loadRootData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) throws IOException {
    if (myFsRootDataLoader != null) {
      myRootsAccessLock.lock();
      try {
        myFsRootDataLoader.loadRootData(getRootsStoragePath(myFsRootDataLoader), id, path, fs);
      }
      finally {
        myRootsAccessLock.unlock();
      }
    }
  }

  void deleteDirectoryRecord(int id) throws IOException {
    if (myFsRootDataLoader != null) {
      myFsRootDataLoader.deleteDirectoryRecord(getRootsStoragePath(myFsRootDataLoader), id);
    }
  }

  void deleteRootRecord(int fileId) throws IOException {
    myRootsAccessLock.lock();
    try {
      myFSConnection.markDirty();

      if (myFsRootDataLoader != null) {
        myFsRootDataLoader.deleteRootRecord(getRootsStoragePath(myFsRootDataLoader), fileId);
      }

      int[] names;
      int[] ids;
      try (final DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, CHILDREN_ATTR)) {
        assert input != null;
        int count = DataInputOutputUtil.readINT(input);

        names = ArrayUtil.newIntArray(count);
        ids = ArrayUtil.newIntArray(count);
        int prevId = 0;
        int prevNameId = 0;
        for (int i = 0; i < count; i++) {
          names[i] = DataInputOutputUtil.readINT(input) + prevNameId;
          ids[i] = DataInputOutputUtil.readINT(input) + prevId;
          prevId = ids[i];
          prevNameId = names[i];
        }
      }

      final int index = ArrayUtil.find(ids, fileId);
      assert index >= 0;

      names = ArrayUtil.remove(names, index);
      ids = ArrayUtil.remove(ids, index);

      try (DataOutputStream output = myAttributeAccessor.writeAttribute(ROOT_RECORD_ID, CHILDREN_ATTR)) {
        saveNameIdSequenceWithDeltas(names, ids, output);
      }
    }
    finally {
      myRootsAccessLock.unlock();
    }
  }

  void ensureLoaded() throws IOException {
    if (myFsRootDataLoader != null) {
      myFsRootDataLoader.ensureLoaded(getRootsStoragePath(myFsRootDataLoader));
    }

    myFSConnection.getAttributeId(CHILDREN_ATTR.getId()); // trigger writing / loading of vfs attribute ids in top level write action
  }

  private static void saveNameIdSequenceWithDeltas(int[] names, int[] ids, DataOutputStream output) throws IOException {
    DataInputOutputUtil.writeINT(output, names.length);
    int prevId = 0;
    int prevNameId = 0;
    for (int i = 0; i < names.length; i++) {
      DataInputOutputUtil.writeINT(output, names[i] - prevNameId);
      DataInputOutputUtil.writeINT(output, ids[i] - prevId);
      prevId = ids[i];
      prevNameId = names[i];
    }
  }

  private @NotNull Path getRootsStoragePath(FsRootDataLoader loader) {
    return myFSConnection.getPersistentFSPaths().getRootsStorage(loader.getName());
  }
}
