// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

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
    int flags = records.getFlags(parentId);
    List<? extends ChildInfo> childrenList = attributeAccessor.readAttributeRaw(parentId, CHILDREN_ATTR, buffer -> {
      int count = DataInputOutputUtil.readINT(buffer);
      if (count == 0) {
        return Collections.emptyList();
      }
      List<ChildInfo> children = new ArrayList<>(count);
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
      return children;
    });
    if (childrenList == null) {
      childrenList = Collections.emptyList();
    }

    if (FSRecordsImpl.areAllChildrenCached(flags)) {
      return ListResult.allCached(parentModCount, childrenList, parentId);
    }
    else {
      return ListResult.notAllCached(parentModCount, childrenList, parentId);
    }
  }

  @Override
  public boolean forEachChild(int fileId,
                              @NotNull IntPredicate childConsumer) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    if (fileId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .listIds() with is a super-root record id(=" + SUPER_ROOT_ID + ") -- use .listRoots() instead");
    }

    Boolean stoppedEarly = attributeAccessor.readAttributeRaw(fileId, CHILDREN_ATTR, buffer -> {
      int count = DataInputOutputUtil.readINT(buffer);
      if(count == 0){
        return false;
      }
      int prevId = fileId;
      int maxID = connection.records().maxAllocatedID();
      for (int i = 0; i < count; i++) {
        int childId = DataInputOutputUtil.readINT(buffer) + prevId;
        if (childConsumer.test(childId)) {
          return true;
        }
        prevId = childId;
        checkChildIdValid(fileId, prevId, i, maxID);
      }

      return false;
    });

    if (stoppedEarly == null) {
      return false;
    }
    return stoppedEarly;
  }

  @Override
  boolean maybeHaveChildren(int fileId) throws IOException {
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

  @Override
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

    int maxAllocatedID = connection.records().maxAllocatedID();
    attributeAccessor.readAttributeRaw(SUPER_ROOT_ID, CHILDREN_ATTR, recordBuffer -> {
      int rootsCount = DataInputOutputUtil.readINT(recordBuffer);
      if (rootsCount < 0) {
        throw new IOException("SUPER_ROOT.CHILDREN attribute is corrupted: roots count(=" + rootsCount + ") must be >=0");
      }
      int[] rootsUrlIds = ArrayUtil.newIntArray(rootsCount);
      int[] rootsIds = ArrayUtil.newIntArray(rootsCount);

      int prevUrlId = 0;
      int prevRootId = 0;
      for (int i = 0; i < rootsCount; i++) {
        int diffUrlId = DataInputOutputUtil.readINT(recordBuffer);
        int diffRootId = DataInputOutputUtil.readINT(recordBuffer);

        if (diffUrlId <= 0) {//'cos urlIds array must be sorted
          throw new IOException("SUPER_ROOT.CHILDREN attribute is corrupted: diffUrlId[" + i + "](=" + diffUrlId + ") must be >0");
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
      return null;
    });

    if (this.rootsIds == null) {
      //rootsIds could be null here only if the callback wasn't called => CHILDREN_ATTR doesn't exist => roots catalog is empty
      this.rootsIds = ArrayUtil.EMPTY_INT_ARRAY;
      this.rootsUrlIds = ArrayUtil.EMPTY_INT_ARRAY;
    }
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
