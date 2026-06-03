// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ChildInfoImpl;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.ChildInfo;
import com.intellij.openapi.vfs.newvfs.persistent.ranges.CompactRangesBuilder;
import com.intellij.openapi.vfs.newvfs.persistent.ranges.FixedRangeCountRangesBuilder;
import com.intellij.openapi.vfs.newvfs.persistent.ranges.RangesList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.IDE_USE_FS_ROOTS_DATA_LOADER;
import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.NULL_FILE_ID;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.util.concurrent.TimeUnit.SECONDS;

@ApiStatus.Internal
public class PersistentFSTreeAccessor {
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(FSRecords.LOG, SECONDS.toMillis(15));

  /**
   * Children are stored under this attribute in one of three formats.
   * <p>
   * The FS super-root ({@link #SUPER_ROOT_ID}) is an exceptional case: both child fileId and child nameId are stored,
   * both diff-compressed -- see {@link #findOrCreateRootRecord(String)} for details.
   * <p>
   * A regular exact-list directory stores {@code [header: varint32] [childId deltas...]}, where {@code header >= 0}
   * is the children count and each child id is diff-compressed from the previous id, starting from {@code parentId}.
   * <p>
   * A regular range-list directory stores {@code header = -rangesCount} followed by diff-compressed half-open ranges
   * {@code [minChildIdInclusive, maxChildIdExclusive)}. Range boundaries are diff-compressed from the previous boundary,
   * starting from {@code parentId}. Range-list is candidate coverage over id space: readers keep only live records whose
   * parent id matches the directory.
   */
  public static final FileAttribute CHILDREN_ATTR = new FileAttribute("FsRecords.DIRECTORY_CHILDREN");


  private static final int MAX_VARINT_SIZE = 5;
  private static final int MAX_RANGES_COUNT = Math.max(1, (VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE - MAX_VARINT_SIZE) /
                                                          (2 * MAX_VARINT_SIZE));
  private static final CompactRangesBuilder RANGE_LIST_BUILDER = new FixedRangeCountRangesBuilder(MAX_RANGES_COUNT);

  /**
   * fileId of super-root, 'root of all roots' record: superficial file record to which all FS roots are
   * attached as children -- see {@link #findOrCreateRootRecord(String)} for details.
   */
  protected static final int SUPER_ROOT_ID = FSRecords.ROOT_FILE_ID;

  protected final PersistentFSAttributeAccessor attributeAccessor;
  protected final PersistentFSRecordAccessor recordAccessor;
  protected final PersistentFSConnection connection;

  protected final @Nullable FsRootDataLoader fsRootDataLoader;

  /**
   * Forces range-list storage for non-empty regular directories with at least this many children; mainly for tests and debugging
   *
   * @see #setStoreChildrenAsRangesListIfMoreThan(int)
   */
  private int storeChildrenAsRangesListIfMoreThan = getIntProperty("vfs.children.store-as-range-list-threshold", Integer.MAX_VALUE);

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
  @ApiStatus.Internal
  public int setStoreChildrenAsRangesListIfMoreThan(int storeChildrenAsRangesListIfMoreThan) {
    int old = this.storeChildrenAsRangesListIfMoreThan;
    this.storeChildrenAsRangesListIfMoreThan = storeChildrenAsRangesListIfMoreThan;
    return old;
  }

  @VisibleForTesting
  public void doSaveChildren(int parentId, @NotNull ListResult toSave) throws IOException {
    if (parentId == SUPER_ROOT_ID) {
      throw new AssertionError(
        "Incorrect call .doSaveChildren() with a super-root record id(=" + SUPER_ROOT_ID + "). " +
        "Super-root is a special file record for internal use, it MUST NOT be used directly");
    }

    List<? extends ChildInfo> children = toSave.children;
    validateChildrenToSave(parentId, children, toSave);

    boolean storeAsRangeList = shouldStoreAsRangeList(parentId, children);
    RangesList ranges;
    if (storeAsRangeList) {
      ranges = buildAndCheckRangeList(parentId, children);
      THROTTLED_LOG.info(
        () -> "Directory[#" + parentId + "] is humongous (children.size: " + children.size() + ") " +
              "=> children persisted as range-list: " +
              "{ranges: " + ranges.rangesCount() + ", totalRangesWidth: " + ranges.totalRangeWidth() +
              ", efficacy: " + (children.size() * 100.0 / ranges.totalRangeWidth()) + "%}"
      );
    }
    else {
      ranges = null;
    }

    try (DataOutputStream record = attributeAccessor.writeAttribute(parentId, CHILDREN_ATTR)) {
      if (storeAsRangeList) {
        writeChildrenAsRangeList(parentId, ranges, record);
      }
      else {
        writeChildrenAsExactList(parentId, children, record);
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
      List<? extends ChildInfo> children = input == null ? Collections.emptyList() :
                                           loadChildren(parentId, records, () -> DataInputOutputUtil.readINT(input));
      return asListResult(parentId, parentModCount, flags, children);
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
      return forEachChild(fileId, () -> DataInputOutputUtil.readINT(input), connection.records(), childConsumer);
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
      return childrenAttributeMayHaveChildren(fileId, () -> DataInputOutputUtil.readINT(input));
    }
  }

  /**
   * Reconstructs children from the DIRECTORY_CHILDREN payload, accepting both the legacy exact-list and the range-list format.
   */
  protected final @NotNull List<? extends ChildInfo> loadChildren(int parentId,
                                                                  @NotNull PersistentFSRecordsStorage records,
                                                                  @NotNull IntReader input) throws IOException {
    int header = readINT(input, parentId, "children header");
    if (header == 0) {
      return Collections.emptyList();
    }
    if (header > 0) {
      return loadExactChildren(parentId, /* childrenCount: */ header, input, records);
    }
    else {//header < 0
      if (header == Integer.MIN_VALUE) {
        throw childrenAttributeCorrupted(parentId, "range-list header must not be Integer.MIN_VALUE");
      }
      return loadRangeListChildren(parentId, /* rangesCount: */ -header, input, records);
    }
  }

  /**
   * Iterates children from a DIRECTORY_CHILDREN payload without materializing the full result list.
   */
  protected final boolean forEachChild(int parentId,
                                       @NotNull IntReader input,
                                       @NotNull PersistentFSRecordsStorage records,
                                       @NotNull IntPredicate childConsumer) throws IOException {
    int header = readINT(input, parentId, "children header");
    if (header == 0) {
      return false;
    }
    if (header > 0) {
      return forEachExactListChild(parentId, /* childrenCount: */header, input, records, childConsumer);
    }
    else {
      if (header == Integer.MIN_VALUE) {
        throw childrenAttributeCorrupted(parentId, "range-list header must not be Integer.MIN_VALUE");
      }
      return forEachRangeListChild(parentId, /* rangesCount: */ -header, input, records, childConsumer);
    }
  }

  /**
   * Preserves the cheap may-have-children check: absent means unknown, zero exact-list means known empty.
   */
  protected final boolean childrenAttributeMayHaveChildren(int parentId,
                                                           @NotNull IntReader input) throws IOException {
    return readINT(input, parentId, "children header") != 0;
  }

  /**
   * Applies the cached/not-cached bit to an already decoded children list.
   */
  protected static @NotNull ListResult asListResult(int parentId,
                                                    int parentModCount,
                                                    @PersistentFS.Attributes int flags,
                                                    @NotNull List<? extends ChildInfo> children) {
    if (FSRecordsImpl.areAllChildrenCached(flags)) {
      return ListResult.allCached(parentModCount, children, parentId);
    }
    else {
      return ListResult.notAllCached(parentModCount, children, parentId);
    }
  }

  private static void validateChildrenToSave(int parentId,
                                             @NotNull List<? extends ChildInfo> children,
                                             @NotNull ListResult toSave) {
    int prevId = parentId;
    for (ChildInfo childInfo : children) {
      int childId = childInfo.getId();
      if (childId <= 0) {
        throw new IllegalArgumentException("ids must be >0 but got: " + childId + "; childInfo: " + childInfo + "; list: " + toSave);
      }
      if (childId == parentId) {
        throw new IllegalArgumentException("Cyclic parent-child relations. parentId=" + parentId + "; list: " + toSave);
      }
      int delta = childId - prevId;
      if (prevId != parentId && delta <= 0) {
        throw new IllegalArgumentException("The list must be sorted by (unique) id but got parentId: " +
                                           parentId + "; delta: " + delta + "; childInfo: " + childInfo + "; prevId: " +
                                           prevId + "; toSave: " + toSave);
      }
      prevId = childId;
    }
  }

  private boolean shouldStoreAsRangeList(int parentId,
                                         @NotNull List<? extends ChildInfo> children) {
    if (children.isEmpty()) {
      return false;
    }
    if (children.size() >= storeChildrenAsRangesListIfMoreThan) {
      return true;
    }

    long exactListSizeUpperBound = (long)MAX_VARINT_SIZE * (children.size() + 1);
    if (exactListSizeUpperBound <= VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE) {
      return false;
    }
    return exactListSerializedSize(parentId, children) > VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE;
  }

  private static int exactListSerializedSize(int parentId,
                                             @NotNull List<? extends ChildInfo> children) {
    //keep the public size accounting as int but use long accumulator here: if the serialized size
    // no longer fits into signed int -- fail instead of silently overflowing the accumulator
    long size = DataInputOutputUtil.sizeOfVarint(children.size());
    int prevId = parentId;
    for (ChildInfo childInfo : children) {
      int childId = childInfo.getId();
      size += DataInputOutputUtil.sizeOfVarint(childId - prevId);
      prevId = childId;
    }
    return Math.toIntExact(size);
  }

  private static void writeChildrenAsExactList(int parentId,
                                               @NotNull List<? extends ChildInfo> children,
                                               @NotNull DataOutput output) throws IOException {
    DataInputOutputUtil.writeINT(output, children.size());

    int prevId = parentId;
    for (ChildInfo childInfo : children) {
      int childId = childInfo.getId();
      DataInputOutputUtil.writeINT(output, childId - prevId);
      prevId = childId;
    }
  }

  private static @NotNull RangesList buildAndCheckRangeList(int parentId,
                                                            @NotNull List<? extends ChildInfo> children) throws FileTooBigException {
    RangesList ranges = RANGE_LIST_BUILDER.build(children);
    int rangesCount = ranges.rangesCount();
    if (rangesCount == 0) {
      throw new IllegalStateException("Range-list is empty: range format must not be used for an empty children list: " + children);
    }

    int header = -rangesCount;
    // Header belongs to DIRECTORY_CHILDREN framing; RangesList serializes only diff-compressed range boundaries.
    //TODO RC: use a checked int-size addition helper for header+payload and throw if the total does not fit into signed int.
    int serializedSize = DataInputOutputUtil.sizeOfVarint(header) + ranges.serializedSize(parentId);
    if (serializedSize > VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE) {
      throw new FileTooBigException(
        "Can't store children for parent #" + parentId + ": range-list is too large: " + serializedSize +
        " b > max(" + VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE + "), children=" + children.size() +
        ", ranges=" + rangesCount
      );
    }
    return ranges;
  }

  private static void writeChildrenAsRangeList(int parentId,
                                               @NotNull RangesList ranges,
                                               @NotNull DataOutput output) throws IOException {
    int header = -ranges.rangesCount();

    DataInputOutputUtil.writeINT(output, header);
    ranges.serializeTo(parentId, output);
  }

  private @NotNull List<ChildInfo> loadExactChildren(int parentId,
                                                     int childrenCount,
                                                     @NotNull IntReader input,
                                                     @NotNull PersistentFSRecordsStorage records) throws IOException {
    List<ChildInfo> children = new ArrayList<>(childrenCount);
    int prevId = parentId;
    int maxAllocatedID = records.maxAllocatedID();
    for (int i = 0; i < childrenCount; i++) {
      int childId = readDiffCompressedInt(input, prevId, parentId, "exact child[" + i + "]");
      checkChildIdValid(parentId, childId, i, maxAllocatedID);
      prevId = childId;

      int nameId = records.getNameId(childId);
      checkNameIdValid(nameId, parentId, childId);
      children.add(new ChildInfoImpl(childId, nameId, null, null, null));
    }
    return children;
  }

  private boolean forEachExactListChild(int parentId,
                                        int childrenCount,
                                        @NotNull IntReader input,
                                        @NotNull PersistentFSRecordsStorage records,
                                        @NotNull IntPredicate childConsumer) throws IOException {
    int prevId = parentId;
    int maxAllocatedID = records.maxAllocatedID();
    for (int i = 0; i < childrenCount; i++) {
      int childId = readDiffCompressedInt(input, prevId, parentId, "exact child[" + i + "]");
      checkChildIdValid(parentId, childId, i, maxAllocatedID);
      if (childConsumer.test(childId)) {
        return true;
      }
      prevId = childId;
    }
    return false;
  }

  private @NotNull List<ChildInfo> loadRangeListChildren(int parentId,
                                                         int rangesCount,
                                                         @NotNull IntReader input,
                                                         @NotNull PersistentFSRecordsStorage records) throws IOException {
    RangesList ranges = readRangesList(parentId, rangesCount, input, records);
    List<ChildInfo> children = new ArrayList<>();
    processRangeListChildren(parentId, ranges, records, (childId, nameId) -> {
      children.add(new ChildInfoImpl(childId, nameId, null, null, null));
      return false;
    });
    return children;
  }

  private boolean forEachRangeListChild(int parentId,
                                        int rangesCount,
                                        @NotNull IntReader input,
                                        @NotNull PersistentFSRecordsStorage records,
                                        @NotNull IntPredicate childConsumer) throws IOException {
    RangesList ranges = readRangesList(parentId, rangesCount, input, records);
    return processRangeListChildren(parentId, ranges, records, (childId, nameId) -> {
      assert nameId > 0 : nameId;
      return childConsumer.test(childId);
    });
  }

  private @NotNull RangesList readRangesList(int parentId,
                                             int rangesCount,
                                             @NotNull IntReader input,
                                             @NotNull PersistentFSRecordsStorage records) throws IOException {
    if (rangesCount <= 0) {
      throw childrenAttributeCorrupted(parentId, "range-list ranges count must be >0 but got: " + rangesCount);
    }

    RangesList ranges = new RangesList(rangesCount);
    int prevBoundary = parentId;
    int maxAllocatedID = records.maxAllocatedID();
    // Ranges are half-open, so maxExclusive may be maxAllocatedID + 1; compare in long to avoid int overflow.
    long maxExclusiveUpperBound = (long)maxAllocatedID + 1;
    int previousMaxExclusive = 0;
    for (int i = 0; i < rangesCount; i++) {
      int minChildId = readDiffCompressedInt(input, prevBoundary, parentId, "range[" + i + "].min");
      int maxExclusiveChildId = readDiffCompressedInt(input, minChildId, parentId, "range[" + i + "].maxExclusive");

      if (minChildId <= NULL_FILE_ID) {
        throw childrenAttributeCorrupted(parentId, "range[" + i + "].min(=" + minChildId + ") is outside valid id range (" +
                                                   NULL_FILE_ID + ".." + maxAllocatedID + "]");
      }
      if (maxExclusiveChildId <= minChildId) {
        throw childrenAttributeCorrupted(parentId, "range[" + i + "] is empty: [" + minChildId + ", " +
                                                   maxExclusiveChildId + ")");
      }
      if (maxExclusiveChildId > maxExclusiveUpperBound) {
        throw childrenAttributeCorrupted(parentId, "range[" + i + "].maxExclusive(=" + maxExclusiveChildId +
                                                   ") is outside valid upper bound " + maxExclusiveUpperBound);
      }
      if (i > 0 && minChildId <= previousMaxExclusive) {
        // On-disk format is canonical: writer must merge adjacent ranges, reader rejects non-canonical payloads.
        throw childrenAttributeCorrupted(parentId, "range[" + i + "] starts at " + minChildId +
                                                   " but previous range ends at " + previousMaxExclusive +
                                                   ": ranges must not intersect or be adjacent");
      }

      ranges.addCanonicalRange(minChildId, maxExclusiveChildId);
      previousMaxExclusive = maxExclusiveChildId;
      prevBoundary = maxExclusiveChildId;
    }
    return ranges;
  }

  private boolean processRangeListChildren(int parentId,
                                           @NotNull RangesList ranges,
                                           @NotNull PersistentFSRecordsStorage records,
                                           @NotNull ChildIdProcessor childProcessor) throws IOException {
    int rangesCount = ranges.rangesCount();
    for (int rangeIndex = 0; rangeIndex < rangesCount; rangeIndex++) {
      int minChildId = ranges.minChildIdInclusive(rangeIndex);
      int maxExclusiveChildId = ranges.maxChildIdExclusive(rangeIndex);
      for (int childId = minChildId; childId < maxExclusiveChildId; childId++) {
        // Range-list stores candidate ids; deleted records and records under another parent are not directory children.
        int flags = records.getFlags(childId);
        if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
          continue;
        }
        if (records.getParent(childId) != parentId) {
          continue;
        }

        int nameId = records.getNameId(childId);
        checkNameIdValid(nameId, parentId, childId);
        if (childProcessor.process(childId, nameId)) {
          return true;
        }
      }
    }
    return false;
  }

  private int readDiffCompressedInt(@NotNull IntReader input,
                                    int previousValue,
                                    int parentId,
                                    @NotNull String valueDescription) throws IOException {
    int delta = readINT(input, parentId, valueDescription);
    long value = (long)previousValue + delta;
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      throw childrenAttributeCorrupted(parentId, valueDescription + " overflows int32 after diff decoding: previous=" +
                                                 previousValue + ", delta=" + delta);
    }
    return (int)value;
  }

  /** parentId & valueDescription are only for exception message */
  private int readINT(@NotNull IntReader input,
                      int parentId,
                      @NotNull String valueDescription) throws IOException {
    try {
      return input.readINT();
    }
    catch (EOFException | BufferUnderflowException e) {
      throw childrenAttributeCorrupted(parentId, "unexpected EOF while reading " + valueDescription, e);
    }
  }

  private @NotNull CorruptedException childrenAttributeCorrupted(int parentId,
                                                                 @NotNull String message) {
    return childrenAttributeCorrupted(parentId, message, null);
  }

  private @NotNull CorruptedException childrenAttributeCorrupted(int parentId,
                                                                 @NotNull String message,
                                                                 @Nullable Throwable cause) {
    String fullMessage = "file[" + parentId + "]." + CHILDREN_ATTR + " attribute is corrupted: " + message +
                         ", VFS.status: {" + connection.describeConsistencyStatus() + "}";
    return cause == null ? new CorruptedException(fullMessage) : new CorruptedException(fullMessage, cause);
  }

  /** Reads values from DIRECTORY_CHILDREN without binding shared parser code to stream or ByteBuffer storage. */
  @FunctionalInterface
  protected interface IntReader {
    int readINT() throws IOException;
  }

  @FunctionalInterface
  private interface ChildIdProcessor {
    boolean process(int childId, int nameId) throws IOException;
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
        throw new CorruptedException(
          "SUPER_ROOT.CHILDREN attribute is corrupted: roots count(=" + rootsCount + ") must be >=0, " +
          "VFS.status: {" + connection.describeConsistencyStatus() + "}"
        );
      }
      int[] rootsUrlIds = ArrayUtil.newIntArray(rootsCount);
      int[] rootsIds = ArrayUtil.newIntArray(rootsCount);

      int prevUrlId = 0;
      int prevRootId = 0;
      for (int i = 0; i < rootsCount; i++) {
        int diffUrlId = DataInputOutputUtil.readINT(input);
        int diffRootId = DataInputOutputUtil.readINT(input);

        if (diffUrlId <= 0) {//'cos urlIds array must be sorted
          throw new CorruptedException(
            "SUPER_ROOT.CHILDREN attribute is corrupted: diffUrlId[" + i + "](=" + diffUrlId + ") must be >0, " +
            "VFS.status: {" + connection.describeConsistencyStatus() + "}");
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
  private void saveUrlAndFileIdsAsDiffCompressed(int[] urlIds,
                                                 int[] fileIds,
                                                 @NotNull DataOutputStream output) throws IOException {
    if (urlIds.length != fileIds.length) {
      throw new IllegalArgumentException(
        "urlIds.length(=" + urlIds.length + ") != fileIds.length(=" + fileIds.length + "), " +
        "VFS.status: {" + connection.describeConsistencyStatus() + "}"
      );
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
          "urlIds: " + Arrays.toString(urlIds) + ", " +
          "VFS.status: {" + connection.describeConsistencyStatus() + "}"
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

  protected void checkNameIdValid(int nameId,
                                  int parentId,
                                  int childId) throws CorruptedException {
    if (nameId == DataEnumerator.NULL_ID) {
      throw new CorruptedException(
        "parentId: " + parentId + ", childId: " + childId + ", nameId: " + nameId + " is invalid " +
        "-> VFS is corrupted (" + connection.describeConsistencyStatus() + ")"
      );
    }
  }

  protected void checkChildIdValid(int parentId,
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
        "-> VFS is corrupted (" + connection.describeConsistencyStatus() + ")"
      );
    }
  }

  @ApiStatus.Internal
  public interface RootsConsumer {
    boolean processRoot(int rootFileId, int rootUrlId);
  }
}
