// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

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

  public PersistentFSTreeAccessor(@NotNull PersistentFSAttributeAccessor attributeAccessor,
                                  @NotNull PersistentFSRecordAccessor recordAccessor,
                                  @NotNull PersistentFSConnection connection) {
    this.attributeAccessor = attributeAccessor;
    this.recordAccessor = recordAccessor;
    this.connection = connection;
    fsRootDataLoader = SystemProperties.getBooleanProperty(IDE_USE_FS_ROOTS_DATA_LOADER, false)
                       ? ApplicationManager.getApplication().getService(FsRootDataLoader.class)
                       : null;
  }

  @VisibleForTesting
  public void doSaveChildren(int parentId, @NotNull ListResult toSave) throws IOException {
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

  public @NotNull ListResult doLoadChildren(int parentId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);
    if (parentId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .doLoadChildren() with a super-root record id(=" + SUPER_ROOT_ID + "). " +
        "Super-root is a special file record for internal use, it MUST NOT be used directly");
    }

    PersistentFSRecordsStorage records = connection.records();
    int parentModCount = records.getModCount(parentId);
    int flags = records.getFlags(parentId);
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

      if (FSRecordsImpl.areAllChildrenCached(flags)) {
        return ListResult.allCached(parentModCount, children, parentId);
      }
      else {
        return ListResult.notAllCached(parentModCount, children, parentId);
      }
    }
  }

  boolean wereChildrenAccessed(int fileId) throws IOException {
    return attributeAccessor.hasAttributePage(fileId, CHILDREN_ATTR);
  }

  /**
   * Scan each child if parentId, and invokes consumer for each childId.
   * Scanning is stopped early if the consumer returns true (='found')
   *
   * @return true, if consumer returns true for any childId passed in, false otherwise
   */
  @VisibleForTesting
  public boolean forEachChild(int fileId,
                              @NotNull IntPredicate childConsumer) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    if (fileId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .listIds() with is a super-root record id(=" + SUPER_ROOT_ID + ") -- use .listRoots() instead");
    }

    try (DataInputStream input = attributeAccessor.readAttribute(fileId, CHILDREN_ATTR)) {
      if (input == null) return false;

      PersistentFSRecordsStorage records = connection.records();
      int maxID = records.maxAllocatedID();

      int count = DataInputOutputUtil.readINT(input);
      int prevId = fileId;
      for (int i = 0; i < count; i++) {
        int childId = DataInputOutputUtil.readINT(input) + prevId;
        if (childConsumer.test(childId)) {
          return true;
        }
        prevId = childId;
        checkChildIdValid(fileId, prevId, i, maxID);
      }
      return false;
    }
  }

  boolean maybeHaveChildren(int fileId) throws IOException {
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

  //Threading: all the root accessing/modifying methods are called under the record/hierarchy lock in FSRecordsImpl.
  // The fields could be updated in cacheRoots() while being called under _read_ lock -- but this is fine, since
  // the underlying source of truth is the attribute, which could be modified only under write lock, so concurrent
  // execution of cacheRoots() is harmless -- it just creates few useless duplicated arrays.

  /** sorted (target for binary search) */
  protected int[] rootsUrlIds = null;
  /** not sorted */
  protected int[] rootsIds = null;

  final int @NotNull [] listRoots() throws IOException {
    ensureRootsCached();
    return rootsIds;
  }

  protected void ensureRootsCached() throws IOException {
    if (rootsIds == null || rootsUrlIds == null) {
      cacheRoots();
    }
  }

  protected void cacheRoots() throws IOException {
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
      if (input == null) {
        this.rootsIds = ArrayUtil.EMPTY_INT_ARRAY;
        this.rootsUrlIds = ArrayUtil.EMPTY_INT_ARRAY;
        return;
      }

      int maxAllocatedID = connection.records().maxAllocatedID();
      int rootsCount = DataInputOutputUtil.readINT(input);
      if (rootsCount < 0) {
        throw new IOException("SUPER_ROOT.CHILDREN attribute is corrupted: roots count(=" + rootsCount + ") must be >=0");
      }
      int[] rootsUrlIds = ArrayUtil.newIntArray(rootsCount);
      int[] rootsIds = ArrayUtil.newIntArray(rootsCount);

      int prevUrlId = 0;
      int prevRootId = 0;
      for (int i = 0; i < rootsCount; i++) {
        int diffUrlId = DataInputOutputUtil.readINT(input);
        int diffRootId = DataInputOutputUtil.readINT(input);

        if (diffUrlId <= 0) {//'cos urlIds array must be sorted
          throw new CorruptedException("SUPER_ROOT.CHILDREN attribute is corrupted: diffUrlId[" + i + "](=" + diffUrlId + ") must be >0");
        }

        int urlId = diffUrlId + prevUrlId;
        int rootId = diffRootId + prevRootId;

        checkChildIdValid(SUPER_ROOT_ID, rootId, i, maxAllocatedID);

        rootsUrlIds[i] = urlId;
        rootsIds[i] = rootId;

        prevUrlId = urlId;
        prevRootId = rootId;
      }
      this.rootsIds = rootsIds;
      this.rootsUrlIds = rootsUrlIds;
    }
  }

  final int findOrCreateRootRecord(@NotNull String rootUrl) throws IOException {
    ensureRootsCached();

    PersistentFSConnection connection = this.connection;
    DataEnumeratorEx<String> names = connection.names();

    int rootUrlId = names.enumerate(rootUrl);
    int rootIndex = Arrays.binarySearch(rootsUrlIds, rootUrlId);
    if (rootIndex >= 0) {
      return rootsIds[rootIndex];
    }
    int insertionIndex = -rootIndex - 1;
    //FIXME RC: use fileIdIndexedStorages instead of emptyList()
    int newRootFileId = recordAccessor.createRecord(Collections.emptyList());
    rootsUrlIds = ArrayUtil.insert(rootsUrlIds, insertionIndex, rootUrlId);
    rootsIds = ArrayUtil.insert(rootsIds, insertionIndex, newRootFileId);

    try (DataOutputStream output = attributeAccessor.writeAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      saveUrlAndFileIdsAsDiffCompressed(rootsUrlIds, rootsIds, output);
      //RC: we should assign connection.records.setNameId(newRootFileId, root_Name_Id), but we don't
      //    have rootNameId here -- we have only rootUrlId. So, rootNameId is assigned to the root
      //    in a PersistentFSImpl.findRoot() method, up the stack
      return newRootFileId;
    }
    catch (FileTooBigException e) {
      //expect FileTooBigException to be thrown from AttributeStorage
      throw new FileTooBigException(
        "Can't add new root (#" + newRootFileId + ", url=[" + rootUrl + "], urlId=" + rootUrlId + ") to the VFS: " +
        "too many roots already (= " + rootsIds.length + ")",
        e
      );
    }
  }

  /**
   * Deletes the rootId entry from the roots catalog. The file-record itself is not deleted!
   *
   * @throws IOException if the rootId is not found in the roots catalog.
   */
  final void deleteRootRecord(int rootId) throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.deleteRootRecord(getRootsStoragePath(fsRootDataLoader), rootId);
    }

    ensureRootsCached();

    int index = ArrayUtil.find(rootsIds, rootId);
    if (index < 0) {
      String rootUrlsString = rootsIds.length < 128 ?
                              Arrays.toString(rootsIds) :
                              Arrays.toString(Arrays.copyOf(rootsIds, 128)) + "...";
      throw new IOException("No root[#" + rootId + "] entry found among roots " + rootUrlsString);
    }

    rootsUrlIds = ArrayUtil.remove(rootsUrlIds, index);
    rootsIds = ArrayUtil.remove(rootsIds, index);

    try (DataOutputStream output = attributeAccessor.writeAttribute(SUPER_ROOT_ID, CHILDREN_ATTR)) {
      saveUrlAndFileIdsAsDiffCompressed(rootsUrlIds, rootsIds, output);
    }
  }

  /**
   * Supplies all the roots into rootConsumer, along with appropriate rootUrlId.
   * Iteration could be interrupted early by returning false from the {@link RootsConsumer#processRoot(int, int)} method
   */
  final void forEachRoot(@NotNull RootsConsumer rootConsumer) throws IOException {
    ensureRootsCached();
    for (int i = 0; i < rootsIds.length; i++) {
      int rootId = rootsIds[i];
      int rootUrlId = rootsUrlIds[i];
      boolean continueProcessing = rootConsumer.processRoot(rootId, rootUrlId);
      if (!continueProcessing) {
        return;
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

  void ensureLoaded() throws IOException {
    if (fsRootDataLoader != null) {
      fsRootDataLoader.ensureLoaded(getRootsStoragePath(fsRootDataLoader));
    }

    connection.enumerateAttributeId(CHILDREN_ATTR.getId()); // trigger writing / loading of vfs attribute ids in top level write action
  }

  /**
   * Serializes urlIds and fileIds arrays into output stream, in diff-compressed format:
   * <pre>
   * {urlIds.length: varint} ({urlId[i]-urlId[i-1]: varint}, {fileId[i]-fileId[i-1]: varint})*
   * </pre>
   * urlIds array must be sorted, urlIds and fileIds arrays must be the same length, and without duplicates -- otherwise
   * {@link IllegalStateException} is thrown
   */
  private static void saveUrlAndFileIdsAsDiffCompressed(int[] urlIds,
                                                        int[] fileIds,
                                                        @NotNull DataOutputStream output) throws IOException {
    if (urlIds.length != fileIds.length) {
      throw new IllegalArgumentException("urlIds.length(=" + urlIds.length + ") != fileIds.length(=" + fileIds.length + ")");
    }
    DataInputOutputUtil.writeINT(output, urlIds.length);
    int prevUrlId = 0;
    int prevFileId = 0;
    for (int i = 0; i < urlIds.length; i++) {
      int urlId = urlIds[i];
      int fileId = fileIds[i];
      int diffUrlId = urlId - prevUrlId;
      int diffFileId = fileId - prevFileId;
      if (diffUrlId <= 0) {
        //MAYBE RC: limit printed urlsIds number to something reasonable, like [i-64..i+64]? Seems like we have VFS with
        //          very high roots count today, so printing them all could be quite a burden for logs reading
        throw new IllegalStateException(
          "urlIds are not sorted: urlIds[" + i + "](=" + urlId + ") <= urlIds[" + (i - 1) + "](=" + prevUrlId + "), " +
          "urlIds: " + Arrays.toString(urlIds)
        );
      }
      DataInputOutputUtil.writeINT(output, diffUrlId);
      DataInputOutputUtil.writeINT(output, diffFileId);
      prevFileId = fileId;
      prevUrlId = urlId;
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

  @ApiStatus.Internal
  public interface RootsConsumer {
    boolean processRoot(int rootFileId, int rootUrlId);
  }
}
