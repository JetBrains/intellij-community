// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER;

@ApiStatus.Internal
public class PersistentFSTreeAccessor {
  /**
   * The attribute is a list of child fileId, diff-compressed -- see {@link #doSaveChildren} for details.
   * The FS super-root ({@link #SUPER_ROOT_ID}) is an exceptional case: there is stored both child fileId
   * AND child nameId, both diff-compressed -- see {@link #findOrCreateRootRecord(String)} for details.
   */
  protected static final FileAttribute CHILDREN_ATTR = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");
  /**
   * fileId of super-root, 'root of all roots' record: superficial file record to which all FS roots are
   * attached as children -- see {@link #findOrCreateRootRecord(String)} for details.
   */
  protected static final int SUPER_ROOT_ID = FSRecords.ROOT_FILE_ID;

  protected final PersistentFSAttributeAccessor attributeAccessor;
  protected final PersistentFSRecordAccessor recordAccessor;
  protected final PersistentFSConnection connection;

  protected final @Nullable FsRootDataLoader fsRootDataLoader;

  PersistentFSTreeAccessor(@NotNull PersistentFSAttributeAccessor attributeAccessor,
                           @NotNull PersistentFSRecordAccessor recordAccessor,
                           @NotNull PersistentFSConnection connection) {
    this.attributeAccessor = attributeAccessor;
    this.recordAccessor = recordAccessor;
    this.connection = connection;
    fsRootDataLoader = SystemProperties.getBooleanProperty(IDE_USE_FS_ROOTS_DATA_LOADER, false)
                       ? ApplicationManager.getApplication().getService(FsRootDataLoader.class)
                       : null;
  }

  void doSaveChildren(int parentId, @NotNull ListResult toSave) throws IOException {
    if (parentId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .doSaveChildren() with a super-root record id(=" + SUPER_ROOT_ID + "). " +
        "Super-root is a special file record for internal use, it MUST NOT be used directly");
    }

    try (DataOutputStream record = attributeAccessor.writeAttribute(parentId, CHILDREN_ATTR)) {
      DataInputOutputUtil.writeINT(record, toSave.children.size());

      int prevId = parentId;
      for (ChildInfo childInfo : toSave.children) {
        int childId = childInfo.getId();
        if (childId <= 0) {
          throw new IllegalArgumentException("ids must be >0 but got: " + childId + "; childInfo: " + childInfo + "; list: " + toSave);
        }
        if (childId == parentId) {
          FSRecords.LOG.error("Cyclic parent-child relations. parentId=" + parentId + "; list: " + toSave);
        }
        else {
          int delta = childId - prevId;
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
  ListResult doLoadChildren(int parentId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);
    if (parentId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .doLoadChildren() with a super-root record id(=" + SUPER_ROOT_ID + "). " +
        "Super-root is a special file record for internal use, it MUST NOT be used directly");
    }

    PersistentFSRecordsStorage records = connection.records();
    int parentModCount = records.getModCount(parentId);
    try (DataInputStream input = attributeAccessor.readAttribute(parentId, CHILDREN_ATTR)) {
      int count = (input == null) ? 0 : DataInputOutputUtil.readINT(input);
      List<ChildInfo> children = (count == 0) ? Collections.emptyList() : new ArrayList<>(count);
      int prevId = parentId;
      int maxAllocatedID = records.maxAllocatedID();
      for (int i = 0; i < count; i++) {
        int childId = DataInputOutputUtil.readINT(input) + prevId;
        checkChildIdValid(parentId, childId, i, maxAllocatedID);

        prevId = childId;
        int nameId = records.getNameId(childId);
        checkNameIdValid(nameId, parentId, childId);
        ChildInfo child = new ChildInfoImpl(childId, nameId, null, null, null);
        children.add(child);
      }

      return new ListResult(parentModCount, children, parentId);
    }
  }

  boolean wereChildrenAccessed(int fileId) throws IOException {
    return attributeAccessor.hasAttributePage(fileId, CHILDREN_ATTR);
  }

  int @NotNull [] listRoots() throws IOException {
    //Roots in VFS are quite special:
    // The root record itself (in connection.records) is just a normal file record, with parentId=NULL_ID,
    // and nameId=names.enumerate(root name).
    //
    // But all roots are _also_ stored as a CHILDREN attribute of special SUPER_ROOT record with reserved id=1.
    // That specific CHILDREN attribute format is different from an ordinary CHILDREN attribute format: it stores
    // both rootIds and root_Url_Ids (both as diff-compressed VARINTs). The thing is: rootUrl != rootName.
    // E.g. for local Linux fs root: name="/", url="file:"
    //
    // We use rootUrl in method findOrCreateRootRecord(rootUrl) -- this is how we uniquely identify root on
    // an actual filesystem -- i.e. we assume rootUrl is a unique way for identify fs node.
    try (DataInputStream input = attributeAccessor.readAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;

      PersistentFSRecordsStorage records = connection.records();
      int maxID = records.maxAllocatedID();

      int count = DataInputOutputUtil.readINT(input);
      int[] roots = ArrayUtil.newIntArray(count);
      int prevId = 0;
      for (int i = 0; i < count; i++) {
        DataInputOutputUtil.readINT(input); // Name
        prevId = roots[i] = DataInputOutputUtil.readINT(input) + prevId; // Id
        checkChildIdValid(SUPER_ROOT_ID, prevId, i, maxID);
      }
      return roots;
    }
  }

  /**
   * @return array if children fileIds for the given fileId
   * MAYBE rename to childrenIds()?
   */
  int @NotNull [] listIds(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    if (fileId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .listIds() with is a super-root record id(=" + SUPER_ROOT_ID + ") -- use .listRoots() instead");
    }

    try (DataInputStream input = attributeAccessor.readAttribute(fileId, CHILDREN_ATTR)) {
      if (input == null) return ArrayUtilRt.EMPTY_INT_ARRAY;

      PersistentFSRecordsStorage records = connection.records();
      int maxID = records.maxAllocatedID();

      int count = DataInputOutputUtil.readINT(input);
      int[] children = ArrayUtil.newIntArray(count);
      int prevId = fileId;
      for (int i = 0; i < count; i++) {
        prevId = children[i] = DataInputOutputUtil.readINT(input) + prevId;
        checkChildIdValid(fileId, prevId, i, maxID);
      }
      return children;
    }
  }

  boolean mayHaveChildren(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    if (fileId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .mayHaveChildren() with is a super-root record id(=" + SUPER_ROOT_ID + ")" +
        "Super-root is a special file record for internal use, it MUST NOT be used directly"
      );
    }

    try (DataInputStream input = attributeAccessor.readAttribute(fileId, CHILDREN_ATTR)) {
      if (input == null) return true;
      int count = DataInputOutputUtil.readINT(input);
      return count != 0;
    }
  }

  int findOrCreateRootRecord(@NotNull String rootUrl) throws IOException {
    PersistentFSConnection connection = this.connection;

    int rootUrlId = connection.names().tryEnumerate(rootUrl);

    int[] rootUrls = ArrayUtilRt.EMPTY_INT_ARRAY;
    int[] rootIds = ArrayUtilRt.EMPTY_INT_ARRAY;
    try (DataInputStream input = attributeAccessor.readAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      if (input != null) {
        int rootsCount = DataInputOutputUtil.readINT(input);
        if (rootsCount < 0) {
          throw new IOException("SUPER_ROOT.CHILDREN attribute is corrupted: roots count(=" + rootsCount + ") must be >=0");
        }
        rootUrls = ArrayUtil.newIntArray(rootsCount);
        rootIds = ArrayUtil.newIntArray(rootsCount);
        int prevRootId = 0;
        int prevUrlId = 0;

        for (int i = 0; i < rootsCount; i++) {
          int urlId = DataInputOutputUtil.readINT(input) + prevUrlId;
          int rootId = DataInputOutputUtil.readINT(input) + prevRootId;
          if (urlId == rootUrlId) {
            checkChildIdValid(SUPER_ROOT_ID, rootId, i, connection.records().maxAllocatedID());
            return rootId;
          }

          prevUrlId = rootUrls[i] = urlId;
          prevRootId = rootIds[i] = rootId;
        }
      }
    }

    rootUrlId = connection.names().enumerate(rootUrl);

    try (DataOutputStream output = attributeAccessor.writeAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      int newRootFileId = recordAccessor.createRecord(Collections.emptyList());

      int index = Arrays.binarySearch(rootIds, newRootFileId);
      if (index >= 0) {
        throw new AssertionError("Newly allocated newRootFileId(=" + newRootFileId + ") already exists in root record: " +
                                 "rootIds(=" + Arrays.toString(rootIds) + "), rootUrls(=" + Arrays.toString(rootUrls) + "), " +
                                 "rootUrl(=" + rootUrl + "), rootUrlId(=" + rootUrlId + ")");
      }
      rootIds = ArrayUtil.insert(rootIds, -index - 1, newRootFileId);
      rootUrls = ArrayUtil.insert(rootUrls, -index - 1, rootUrlId);

      saveNameIdSequenceWithDeltas(rootUrls, rootIds, output);
      //RC: we should assign connection.records.setNameId(newRootFileId, root_Name_Id), but we don't
      //    have rootNameId here -- we have only rootUrlId. Actually, rootNameId is assigned to the root
      //    in a PersistentFSImpl.findRoot() method
      return newRootFileId;
    }
  }

  /**
   * Supplies all the roots into rootConsumer, along with appropriate rootUrlId.
   * Iteration could be interrupted early by returning false from the {@link RootsConsumer#processRoot(int, int)} method
   */
  void forEachRoot(@NotNull RootsConsumer rootConsumer) throws IOException {
    try (DataInputStream input = attributeAccessor.readAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      if (input != null) {
        int count = DataInputOutputUtil.readINT(input);
        if (count < 0) {
          throw new IOException("SUPER_ROOT.CHILDREN attribute is corrupted: roots count(=" + count + ") must be >=0");
        }
        int prevId = 0;
        int prevUrlId = 0;

        for (int i = 0; i < count; i++) {
          int rootUrlId = DataInputOutputUtil.readINT(input) + prevUrlId;
          int rootId = DataInputOutputUtil.readINT(input) + prevId;
          prevUrlId = rootUrlId;
          prevId = rootId;

          boolean continueProcessing = rootConsumer.processRoot(rootId, rootUrlId);
          if (!continueProcessing) {
            return;
          }
        }
      }
    }
  }

  void loadDirectoryData(int id,
                         @NotNull VirtualFile parent,
                         @NotNull CharSequence childName,
                         @NotNull NewVirtualFileSystem fs) throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.loadDirectoryData(getRootsStoragePath(fsRootDataLoader), id, parent, childName, fs);
    }
  }

  void loadRootData(int id, @NotNull String path, @NotNull NewVirtualFileSystem fs) throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.loadRootData(getRootsStoragePath(fsRootDataLoader), id, path, fs);
    }
  }

  void deleteDirectoryRecord(int id) throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.deleteDirectoryRecord(getRootsStoragePath(fsRootDataLoader), id);
    }
  }

  void deleteRootRecord(int fileId) throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.deleteRootRecord(getRootsStoragePath(fsRootDataLoader), fileId);
    }

    int[] names;
    int[] ids;
    try (DataInputStream input = attributeAccessor.readAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
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

    int index = ArrayUtil.find(ids, fileId);
    assert index >= 0;

    names = ArrayUtil.remove(names, index);
    ids = ArrayUtil.remove(ids, index);

    try (DataOutputStream output = attributeAccessor.writeAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      saveNameIdSequenceWithDeltas(names, ids, output);
    }
  }

  void ensureLoaded() throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.ensureLoaded(getRootsStoragePath(fsRootDataLoader));
    }

    connection.enumerateAttributeId(CHILDREN_ATTR.getId()); // trigger writing / loading of vfs attribute ids in top level write action
  }

  static void saveNameIdSequenceWithDeltas(int[] names, int[] ids, DataOutputStream output) throws IOException {
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
    return connection.paths().getRootsStorage(loader.getName());
  }

  protected static void checkNameIdValid(int nameId,
                                         int parentId,
                                         int childId) throws CorruptedException {
    if (nameId == DataEnumerator.NULL_ID) {
      throw new CorruptedException("parentId: " + parentId + ", childId: " + childId + ", nameId: " + nameId + " is invalid");
    }
  }

  protected static void checkChildIdValid(int parentId,
                                          int childId,
                                          int childNo,
                                          int maxAllocatedID) throws CorruptedException {
    if (childId < SUPER_ROOT_ID || maxAllocatedID < childId) {
      //RC: generally we throw IndexOutOfBoundsException for id out of bounds -- because this is just an invalid
      // argument, i.e. 'error on caller side'. But if VFS guts are the source of invalid id -- e.g. CHILDREN
      // -- it is not a caller error, but VFS corruption:
      throw new CorruptedException(
        "file[" + parentId + "].child[" + childNo + "][#" + childId + "] is out of valid/allocated id range" +
        " (" + SUPER_ROOT_ID + ".." + maxAllocatedID + "] " +
        "-> VFS is corrupted (was IDE forcibly terminated?)");
    }
  }

  interface RootsConsumer {
    boolean processRoot(int rootFileId, int rootUrlId);
  }
}
