// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Subclass overwrites few attribute-accessing methods to re-implement them over {@link AttributesStorageOverBlobStorage}.
 * That storage allows raw access to the underlying {@link java.nio.ByteBuffer} -- and the subclass tries to utilize
 * that option to bypass copy into byte[], and read from it via {@link java.io.InputStream}.
 */
public class PersistentFSTreeRawAccessor extends PersistentFSTreeAccessor {
  PersistentFSTreeRawAccessor(final @NotNull PersistentFSAttributeAccessor attributeAccessor,
                              final @NotNull PersistentFSConnection connection) {
    super(attributeAccessor, connection);
    if (!myAttributeAccessor.supportsRawAccess()) {
      throw new IllegalArgumentException("attributesAccessor must .supportsRawAccess(): " + attributeAccessor);
    }
  }

  @Override
  @NotNull ListResult doLoadChildren(final int parentId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(parentId);

    final PersistentFSRecordsStorage records = myFSConnection.getRecords();

    //MAYBE RC: .listIds() and .doLoadChildren() both contains same code for reading&parsing children array. It seems
    //         they were implemented this way for optimization i.e. to avoid creating childrenIds array. Could be
    //         useful to check does it really matter -- otherwise code could be simplified, as below:
    //final int[] childrenIds = listIds(parentId);
    //if(childrenIds.length == 0){
    //  return new ListResult(Collections.emptyList(), parentId);
    //}else{
    //  final List<ChildInfo> children =  new ArrayList<>(childrenIds.length);
    //  for (int childId : childrenIds) {
    //    final int nameId = records.getNameId(childId);
    //    final ChildInfo child = new ChildInfoImpl(childId, nameId, null, null, null);
    //    children.add(child);
    //  }
    //  return new ListResult(children, parentId);
    //}

    final ListResult result = myAttributeAccessor.readAttributeRaw(parentId, CHILDREN_ATTR, buffer -> {
      final int count = DataInputOutputUtil.readINT(buffer);
      final List<ChildInfo> children = (count == 0) ? Collections.emptyList() : new ArrayList<>(count);
      int prevId = parentId;
      for (int i = 0; i < count; i++) {
        final int childId = DataInputOutputUtil.readINT(buffer) + prevId;
        prevId = childId;
        final int nameId = records.getNameId(childId);
        final ChildInfo child = new ChildInfoImpl(childId, nameId, null, null, null);
        children.add(child);
      }
      return new ListResult(children, parentId);
    });
    if (result == null) {
      return new ListResult(Collections.emptyList(), parentId);
    }
    return result;
  }

  @Override
  int @NotNull [] listIds(final int fileId) throws IOException {
    final int[] childrenIds = myAttributeAccessor.readAttributeRaw(fileId, CHILDREN_ATTR, buffer -> {
      final int count = DataInputOutputUtil.readINT(buffer);
      final int[] result = ArrayUtil.newIntArray(count);
      int prevId = fileId;
      for (int i = 0; i < count; i++) {
        prevId = result[i] = DataInputOutputUtil.readINT(buffer) + prevId;
      }
      return result;
    });
    if (childrenIds == null) {
      return ArrayUtilRt.EMPTY_INT_ARRAY;
    }
    return childrenIds;
  }

  @Override
  boolean mayHaveChildren(final int fileId) throws IOException {
    final Boolean hasChildren = myAttributeAccessor.readAttributeRaw(fileId, CHILDREN_ATTR, buffer -> {
      final int count = DataInputOutputUtil.readINT(buffer);
      return Boolean.valueOf(count != 0);
    });
    if (hasChildren == null) {
      return true; //we don't know about children => maybe have, maybe not...
    }
    return hasChildren.booleanValue();
  }

  //@Override
  //void doSaveChildren(final int parentId,
  //                    final @NotNull ListResult toSave) throws IOException {
  //  final List<? extends ChildInfo> children = toSave.children;
  //  {
  //    int prevId = parentId;
  //    for (ChildInfo childInfo : children) {
  //      final int childId = childInfo.getId();
  //      if (childId <= 0) {
  //        throw new IllegalArgumentException("ids must be >0 but got: " + childId + "; childInfo: " + childInfo + "; list: " + toSave);
  //      }
  //      if (childId == parentId) {
  //        FSRecords.LOG.error("Cyclic parent-child relations. parentId=" + parentId + "; list: " + toSave);
  //      }
  //      else {
  //        final int delta = childId - prevId;
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
  //      final int childId = childInfo.getId();
  //      if (childId == parentId) {
  //        continue;//skip, already logged above
  //      }
  //      final int delta = childId - prevId;
  //      DataInputOutputUtil.writeINT(buffer, delta);
  //      prevId = childId;
  //    }
  //    return buffer;
  //  });
  //}
}
