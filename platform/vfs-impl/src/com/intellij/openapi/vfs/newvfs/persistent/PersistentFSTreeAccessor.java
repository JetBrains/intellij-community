// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.function.IntSupplier;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER;

final class PersistentFSTreeAccessor {
  private static final FileAttribute ourChildrenAttr = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");

  @NotNull
  private final PersistentFSAttributeAccessor myAttributeAccessor;
  private final PersistentFSConnection myFSConnection;
  private final @Nullable FsRootDataLoader myFsRootDataLoader;
  private static final int ROOT_RECORD_ID = 1;

  PersistentFSTreeAccessor(@NotNull PersistentFSAttributeAccessor attributeAccessor, @NotNull PersistentFSConnection connection) {
    myAttributeAccessor = attributeAccessor;
    myFSConnection = connection;
    myFsRootDataLoader = SystemProperties.getBooleanProperty(IDE_USE_FS_ROOTS_DATA_LOADER, false)
                       ? ContainerUtil.getFirstItem(FsRootDataLoader.getEP_NAME().getExtensionList())
                       : null;
  }

  void doSaveChildren(int parentId, @NotNull ListResult toSave) throws IOException {
    myFSConnection.markDirty();
    try (DataOutputStream record = myAttributeAccessor.writeAttribute(parentId, ourChildrenAttr)) {
      DataInputOutputUtil.writeINT(record, toSave.children.size());

      int prevId = parentId;
      for (ChildInfo childInfo : toSave.children) {
        int childId = childInfo.getId();
        if (childId <= 0) {
          throw new IllegalArgumentException("ids must be >0 but got: "+childId+"; childInfo: "+childInfo+"; list: "+toSave);
        }
        if (childId == parentId) {
          FSRecords.LOG.error("Cyclic parent-child relations. parentId="+parentId+"; list: "+toSave);
        }
        else {
          int delta = childId - prevId;
          if (prevId != parentId && delta <= 0) {
            throw new IllegalArgumentException("The list must be sorted by (unique) id but got parentId: " + parentId  + "; delta: " + delta+"; childInfo: "+childInfo+"; prevId: "+prevId+"; toSave: "+toSave);
          }
          DataInputOutputUtil.writeINT(record, delta);
          prevId = childId;
        }
      }
    }
  }

  @NotNull
  ListResult doLoadChildren(int parentId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);

    try (DataInputStream input = myAttributeAccessor.readAttribute(parentId, ourChildrenAttr)) {
      int count = input == null ? 0 : DataInputOutputUtil.readINT(input);
      List<ChildInfo> result = count == 0 ? Collections.emptyList() : new ArrayList<>(count);
      int prevId = parentId;
      for (int i = 0; i < count; i++) {
        int id = DataInputOutputUtil.readINT(input) + prevId;
        prevId = id;
        int nameId = myFSConnection.getRecords().getNameId(id);
        ChildInfo child = new ChildInfoImpl(id, nameId, null, null, null);
        result.add(child);
      }
      return new ListResult(result, parentId);
    }
  }

  boolean wereChildrenAccessed(int id) throws IOException {
    return myAttributeAccessor.hasAttributePage(id, ourChildrenAttr);
  }

  int @NotNull [] listRoots() throws IOException {
    try (DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
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

  int @NotNull [] listIds(int id) throws IOException {
    try (final DataInputStream input = myAttributeAccessor.readAttribute(id, ourChildrenAttr)) {
      if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;
      final int count = DataInputOutputUtil.readINT(input);
      final int[] result = ArrayUtil.newIntArray(count);
      int prevId = id;
      for (int i = 0; i < count; i++) {
        prevId = result[i] = DataInputOutputUtil.readINT(input) + prevId;
      }
      return result;
    }
  }

  boolean mayHaveChildren(int id) throws IOException {
    try (final DataInputStream input = myAttributeAccessor.readAttribute(id, ourChildrenAttr)) {
      if (input == null) return true;
      final int count = DataInputOutputUtil.readINT(input);
      return count != 0;
    }
  }

  int findOrCreateRootRecord(@NotNull String rootUrl, @NotNull IntSupplier newRecord) throws IOException {
    PersistentFSConnection connection = myFSConnection;

    int root = connection.getNames().tryEnumerate(rootUrl);

    int[] names = ArrayUtilRt.EMPTY_INT_ARRAY;
    int[] ids = ArrayUtilRt.EMPTY_INT_ARRAY;
    try (final DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
      if (input != null) {
        final int count = DataInputOutputUtil.readINT(input);
        names = ArrayUtil.newIntArray(count);
        ids = ArrayUtil.newIntArray(count);
        int prevId = 0;
        int prevNameId = 0;

        for (int i = 0; i < count; i++) {
          final int name = DataInputOutputUtil.readINT(input) + prevNameId;
          final int id = DataInputOutputUtil.readINT(input) + prevId;
          if (name == root) {
            return id;
          }

          prevNameId = names[i] = name;
          prevId = ids[i] = id;
        }
      }
    }

    connection.markDirty();
    root = connection.getNames().enumerate(rootUrl);

    int id;
    try (DataOutputStream output = myAttributeAccessor.writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
      id = newRecord.getAsInt();

      int index = Arrays.binarySearch(ids, id);
      ids = ArrayUtil.insert(ids, -index - 1, id);
      names = ArrayUtil.insert(names, -index - 1, root);

      saveNameIdSequenceWithDeltas(names, ids, output);
    }

    return id;
  }

  void loadDirectoryData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) throws IOException {
    if (myFsRootDataLoader != null) {
      myFsRootDataLoader.loadDirectoryData(getRootsStoragePath(), id, path, fs);
    }
  }

  void loadRootData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) throws IOException {
    if (myFsRootDataLoader != null) {
      myFsRootDataLoader.loadRootData(getRootsStoragePath(), id, path, fs);
    }
  }

  void deleteRootRecord(int fileId) throws IOException {
    myFSConnection.markDirty();

    if (myFsRootDataLoader != null) {
      myFsRootDataLoader.deleteRootRecord(getRootsStoragePath(), fileId);
    }

    int[] names;
    int[] ids;
    try (final DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
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

    try (DataOutputStream output = myAttributeAccessor.writeAttribute(ROOT_RECORD_ID, ourChildrenAttr)) {
      saveNameIdSequenceWithDeltas(names, ids, output);
    }
  }

  void ensureLoaded() throws IOException {
    myFSConnection.getAttributeId(ourChildrenAttr.getId()); // trigger writing / loading of vfs attribute ids in top level write action
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

  @NotNull
  private Path getRootsStoragePath() {
    return myFSConnection.getPersistentFSPaths().getRootsStorage(myFsRootDataLoader.getName());
  }
}
