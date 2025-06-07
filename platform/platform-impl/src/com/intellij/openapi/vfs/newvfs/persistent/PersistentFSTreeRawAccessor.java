// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Subclass overwrites few attribute-accessing methods to re-implement them over {@link AttributesStorageOverBlobStorage}.
 * That storage allows raw access to the underlying {@link java.nio.ByteBuffer} -- and the subclass tries to utilize
 * that option to bypass copy into byte[], and read from it via {@link java.io.InputStream}.
 */
@ApiStatus.Internal
public final class PersistentFSTreeRawAccessor extends PersistentFSTreeAccessor {
  @VisibleForTesting
  public PersistentFSTreeRawAccessor(@NotNull PersistentFSAttributeAccessor attributeAccessor,
                              @NotNull PersistentFSRecordAccessor recordAccessor,
                              @NotNull PersistentFSConnection connection) {
    super(attributeAccessor, recordAccessor, connection);

    if (!this.attributeAccessor.supportsRawAccess()) {
      throw new IllegalArgumentException("attributesAccessor must .supportsRawAccess(): " + attributeAccessor);
    }
  }

  @Override
  public @NotNull ListResult doLoadChildren(int parentId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);
    if (parentId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .doLoadChildren() with a super-root record id(=" + SUPER_ROOT_ID + "). " +
        "Super-root is a special file record for internal use, it MUST NOT be used directly");
    }

    PersistentFSRecordsStorage records = connection.records();

    //MAYBE RC: .listIds() and .doLoadChildren() both contains same code for reading&parsing children array. It seems
    //         they were implemented this way for optimization i.e. to avoid creating childrenIds array. Could be
    //         useful to check does it really matter -- otherwise code could be simplified, as below:
    //int[] childrenIds = listIds(parentId);
    //if(childrenIds.length == 0){
    //  return new ListResult(Collections.emptyList(), parentId);
    //}else{
    //  List<ChildInfo> children =  new ArrayList<>(childrenIds.length);
    //  for (int childId : childrenIds) {
    //    int nameId = records.getNameId(childId);
    //    ChildInfo child = new ChildInfoImpl(childId, nameId, null, null, null);
    //    children.add(child);
    //  }
    //  return new ListResult(children, parentId);
    //}

    int parentModCount = records.getModCount(parentId);
    ListResult result = attributeAccessor.readAttributeRaw(parentId, CHILDREN_ATTR, buffer -> {
      int count = DataInputOutputUtil.readINT(buffer);
      List<ChildInfo> children = (count == 0) ? Collections.emptyList() : new ArrayList<>(count);
      int maxID = records.maxAllocatedID();
      int prevId = parentId;
      for (int i = 0; i < count; i++) {
        int childId = DataInputOutputUtil.readINT(buffer) + prevId;
        checkChildIdValid(parentId, childId, i, maxID);
        prevId = childId;
        int nameId = records.getNameId(childId);
        checkNameIdValid(nameId, parentId, childId);
        ChildInfo child = new ChildInfoImpl(childId, nameId, null, null, null);
        children.add(child);
      }
      return new ListResult(parentModCount, children, parentId);
    });
    if (result == null) {
      return new ListResult(parentModCount, Collections.emptyList(), parentId);
    }
    return result;
  }

  @Override
  public int @NotNull [] listIds(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    if (fileId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .listIds() with is a super-root record id(=" + SUPER_ROOT_ID + ") -- use .listRoots() instead");
    }

    int[] childrenIds = attributeAccessor.readAttributeRaw(fileId, CHILDREN_ATTR, buffer -> {
      int count = DataInputOutputUtil.readINT(buffer);
      int[] result = ArrayUtil.newIntArray(count);
      int prevId = fileId;
      int maxID = connection.records().maxAllocatedID();
      for (int i = 0; i < count; i++) {
        prevId = result[i] = DataInputOutputUtil.readINT(buffer) + prevId;
        checkChildIdValid(fileId, prevId, i, maxID);
      }
      return result;
    });
    if (childrenIds == null) {
      return ArrayUtilRt.EMPTY_INT_ARRAY;
    }
    return childrenIds;
  }

  @Override
  boolean mayHaveChildren(int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    if (fileId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .mayHaveChildren() with is a super-root record id(=" + SUPER_ROOT_ID + ")" +
        "Super-root is a special file record for internal use, it MUST NOT be used directly"
      );
    }

    Boolean hasChildren = attributeAccessor.readAttributeRaw(fileId, CHILDREN_ATTR, buffer -> {
      int count = DataInputOutputUtil.readINT(buffer);
      return Boolean.valueOf(count != 0);
    });
    if (hasChildren == null) {
      return true; //we don't know about children => maybe have, maybe not...
    }
    return hasChildren.booleanValue();
  }

  //@Override
  //void doSaveChildren(int parentId,
  //                    @NotNull ListResult toSave) throws IOException {
  //  List<? extends ChildInfo> children = toSave.children;
  //  {
  //    int prevId = parentId;
  //    for (ChildInfo childInfo : children) {
  //      int childId = childInfo.getId();
  //      if (childId <= 0) {
  //        throw new IllegalArgumentException("ids must be >0 but got: " + childId + "; childInfo: " + childInfo + "; list: " + toSave);
  //      }
  //      if (childId == parentId) {
  //        FSRecords.LOG.error("Cyclic parent-child relations. parentId=" + parentId + "; list: " + toSave);
  //      }
  //      else {
  //        int delta = childId - prevId;
  //        if (prevId != parentId && delta <= 0) {
  //          throw new IllegalArgumentException("The list must be sorted by (unique) id but got parentId: " +
  //                                             parentId + "; delta: " + delta + "; childInfo: " + childInfo + "; prevId: " +
  //                                             prevId + "; toSave: " + toSave);
  //        }
  //        prevId = childId;
  //      }
  //    }
  //  }
  //  myFSConnection.markDirty();
  //  myAttributeAccessor.writeAttributeRaw(parentId, CHILDREN_ATTR, buffer -> {
  //    DataInputOutputUtil.writeINT(buffer, children.size());
  //
  //    int prevId = parentId;
  //    for (ChildInfo childInfo : children) {
  //      int childId = childInfo.getId();
  //      if (childId == parentId) {
  //        continue;//skip, already logged above
  //      }
  //      int delta = childId - prevId;
  //      DataInputOutputUtil.writeINT(buffer, delta);
  //      prevId = childId;
  //    }
  //    return buffer;
  //  });
  //}
}
