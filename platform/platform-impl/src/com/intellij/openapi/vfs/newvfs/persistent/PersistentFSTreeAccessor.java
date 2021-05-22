// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

final class PersistentFSTreeAccessor {
  private static final FileAttribute ourChildrenAttr = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");

  @NotNull
  private final PersistentFSAttributeAccessor myAttributeAccessor;
  private final boolean myStoreRootsSeparately;
  private static final int ROOT_RECORD_ID = 1;

  PersistentFSTreeAccessor(@NotNull PersistentFSAttributeAccessor attributeAccessor, boolean storeRootsSeparately) {
    myAttributeAccessor = attributeAccessor;
    myStoreRootsSeparately = storeRootsSeparately;
  }

  void doSaveChildren(int parentId, @NotNull ListResult toSave, @NotNull PersistentFSConnection connection) throws IOException {
    connection.markDirty();
    try (DataOutputStream record = myAttributeAccessor.writeAttribute(parentId, ourChildrenAttr, connection)) {
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
  ListResult doLoadChildren(int parentId, @NotNull PersistentFSConnection connection) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);

    try (DataInputStream input = myAttributeAccessor.readAttribute(parentId, ourChildrenAttr, connection)) {
      int count = input == null ? 0 : DataInputOutputUtil.readINT(input);
      List<ChildInfo> result = count == 0 ? Collections.emptyList() : new ArrayList<>(count);
      int prevId = parentId;
      for (int i = 0; i < count; i++) {
        int id = DataInputOutputUtil.readINT(input) + prevId;
        prevId = id;
        int nameId = connection.getRecords().getNameId(id);
        ChildInfo child = new ChildInfoImpl(id, nameId, null, null, null);
        result.add(child);
      }
      return new ListResult(result);
    }
  }

  boolean wereChildrenAccessed(int id, @NotNull PersistentFSConnection connection) throws IOException {
    return myAttributeAccessor.hasAttributePage(id, ourChildrenAttr, connection);
  }

  int @NotNull [] listRoots(@NotNull PersistentFSConnection connection) throws IOException {
    if (myStoreRootsSeparately) {
      IntList result = new IntArrayList();

      try (LineNumberReader stream = new LineNumberReader(Files.newBufferedReader(connection.getPersistentFSPaths().getRootsFile()))) {
        String str;
        while ((str = stream.readLine()) != null) {
          int index = str.indexOf(' ');
          int id = Integer.parseInt(str.substring(0, index));
          result.add(id);
        }
      }
      catch (FileNotFoundException ignored) {
      }
      return result.toIntArray();
    }
    else {
      try (DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, ourChildrenAttr, connection)) {
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
  }

  int @NotNull [] listIds(int id, @NotNull PersistentFSConnection connection) throws IOException {
    try (final DataInputStream input = myAttributeAccessor.readAttribute(id, ourChildrenAttr, connection)) {
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

  boolean mayHaveChildren(int id, @NotNull PersistentFSConnection connection) throws IOException {
    try (final DataInputStream input = myAttributeAccessor.readAttribute(id, ourChildrenAttr, connection)) {
      if (input == null) return true;
      final int count = DataInputOutputUtil.readINT(input);
      return count != 0;
    }
  }

  int findOrCreateRootRecord(@NotNull String rootUrl, @NotNull PersistentFSConnection connection, @NotNull IntSupplier newRecord) throws IOException {
    if (myStoreRootsSeparately) {
      try (LineNumberReader stream = new LineNumberReader(Files.newBufferedReader(connection.getPersistentFSPaths().getRootsFile()))) {
        String str;
        while ((str = stream.readLine()) != null) {
          int index = str.indexOf(' ');

          if (str.substring(index + 1).equals(rootUrl)) {
            return Integer.parseInt(str.substring(0, index));
          }
        }
      }
      catch (FileNotFoundException ignored) {
      }

      connection.markDirty();
      try (Writer stream = Files.newBufferedWriter(connection.getPersistentFSPaths().getRootsFile(), StandardOpenOption.APPEND)) {
        int id = newRecord.getAsInt();
        stream.write(id + " " + rootUrl + "\n");
        return id;
      }
    }

    int root = connection.getNames().tryEnumerate(rootUrl);

    int[] names = ArrayUtilRt.EMPTY_INT_ARRAY;
    int[] ids = ArrayUtilRt.EMPTY_INT_ARRAY;
    try (final DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, ourChildrenAttr, connection)) {
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
    try (DataOutputStream output = myAttributeAccessor.writeAttribute(ROOT_RECORD_ID, ourChildrenAttr, connection)) {
      id = newRecord.getAsInt();

      int index = Arrays.binarySearch(ids, id);
      ids = ArrayUtil.insert(ids, -index - 1, id);
      names = ArrayUtil.insert(names, -index - 1, root);

      saveNameIdSequenceWithDeltas(names, ids, output);
    }

    return id;
  }

  void deleteRootRecord(int fileId, @NotNull PersistentFSConnection connection) throws IOException {
    connection.markDirty();
    if (myStoreRootsSeparately) {
      List<String> rootsThatLeft = new ArrayList<>();
      try (LineNumberReader stream = new LineNumberReader(Files.newBufferedReader(connection.getPersistentFSPaths().getRootsFile()))) {
        String str;
        while((str = stream.readLine()) != null) {
          int index = str.indexOf(' ');
          int rootId = Integer.parseInt(str.substring(0, index));
          if (rootId != fileId) {
            rootsThatLeft.add(str);
          }
        }
      }
      catch (FileNotFoundException ignored) {}

      try (Writer stream = Files.newBufferedWriter(connection.getPersistentFSPaths().getRootsFile())) {
        for (String line : rootsThatLeft) {
          stream.write(line);
          stream.write("\n");
        }
      }
      return;
    }

    int[] names;
    int[] ids;
    try (final DataInputStream input = myAttributeAccessor.readAttribute(ROOT_RECORD_ID, ourChildrenAttr, connection)) {
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

    try (DataOutputStream output = myAttributeAccessor.writeAttribute(ROOT_RECORD_ID, ourChildrenAttr, connection)) {
      saveNameIdSequenceWithDeltas(names, ids, output);
    }
  }

  void ensureLoaded(@NotNull PersistentFSConnection connection) throws IOException {
    connection.getAttributeId(ourChildrenAttr.getId()); // trigger writing / loading of vfs attribute ids in top level write action
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
}
