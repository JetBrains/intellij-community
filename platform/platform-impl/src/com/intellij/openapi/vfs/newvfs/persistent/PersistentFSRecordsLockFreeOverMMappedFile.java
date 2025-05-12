// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorage.Page;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.Unmappable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.SystemProperties.getIntProperty;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * Implementation uses memory-mapped file (real one, not our emulation of it via {@link com.intellij.util.io.FilePageCache}).
 */
@ApiStatus.Internal
public final class PersistentFSRecordsLockFreeOverMMappedFile implements PersistentFSRecordsStorage,
                                                                         IPersistentFSRecordsStorage,
                                                                         Unmappable {
  /**
   * How many un-allocated records (i.e. after {@link #maxAllocatedID()}) to check to be empty (all-zero)
   * by default, if wasClosedProperly=true.
   * Everything in the file after {@link #maxAllocatedID()} should be 0 -- but EA-984945 shows sometimes it
   * is not 0, so this self-check was introduced: scan first N records in yet-un-allocated region, and check
   * all the bytes are 0.
   * Set value to 0 to disable the check altogether.
   */
  private static final int UNALLOCATED_RECORDS_TO_CHECK_ZEROED_REGULAR = getIntProperty("vfs.check-unallocated-records-zeroed", 4);
  /** How many records to check if wasClosedProperly=false (i.e. app likely was crashed/killed) */
  private static final int UNALLOCATED_RECORDS_TO_CHECK_ZEROED_CRASHED = UNALLOCATED_RECORDS_TO_CHECK_ZEROED_REGULAR * 100;

  @VisibleForTesting
  @ApiStatus.Internal
  public static final class FileHeader {
    //Forked from PersistentFSHeaders since mmapped impl gained more and more pecularities

    //@formatter:off

    static final int VERSION_OFFSET                         =  0;  // int32
    /**
     * For mmapped implementation file size is page-aligned, we can't calculate records size from it.
     * Instead, we store allocated records count in header, in a reserved field (HEADER_RESERVED_OFFSET_1)
     */
    static final int RECORDS_ALLOCATED_OFFSET               =  4;  // int32
    static final int GLOBAL_MOD_COUNT_OFFSET                =  8;  // int32
    /** Keeps owner process pid */
    static final int OWNER_PROCESS_ID_OFFSET                = 12;  // int32

    //int64 fields must be int64-aligned, so int64 fields are grouped together:

    static final int CREATION_TIMESTAMP_OFFSET              = 16;  // int64
    /** Timestamp of ownership acquisition (ms, unix origin) */
    static final int OWNERSHIP_ACQUIRED_TIMESTAMP_OFFSET    = 24;  // int64

    static final int ERRORS_ACCUMULATED_OFFSET              = 32;  // int32


    //reserve couple int32 header fields for the generations to come
    //Header size must be int64-aligned, so records start on int64-aligned offset
    public static final int HEADER_SIZE                            = 40;

    //@formatter:on
  }

  public static final int NULL_OWNER_PID = 0;

  @VisibleForTesting
  @ApiStatus.Internal
  public static final class RecordLayout {
    //@formatter:off
    static final int PARENT_REF_OFFSET        = 0;   //int32
    static final int NAME_REF_OFFSET          = 4;   //int32
    static final int FLAGS_OFFSET             = 8;   //int32
    static final int ATTR_REF_OFFSET          = 12;  //int32
    static final int CONTENT_REF_OFFSET       = 16;  //int32
    static final int MOD_COUNT_OFFSET         = 20;  //int32

    //RC: moved TIMESTAMP 1 field down so both LONG fields are 8-byte aligned (for atomic accesses alignment is important)
    static final int TIMESTAMP_OFFSET         = 24;  //int64
    static final int LENGTH_OFFSET            = 32;  //int64

    public static final int RECORD_SIZE_IN_BYTES     = 40;
    //@formatter:on
  }

  public static final int DEFAULT_MAPPED_CHUNK_SIZE = getIntProperty("vfs.records-storage.memory-mapped.mapped-chunk-size", 1 << 26);//64Mb

  private static final VarHandle INT_HANDLE = byteBufferViewVarHandle(int[].class, nativeOrder()).withInvokeExactBehavior();
  private static final VarHandle LONG_HANDLE = byteBufferViewVarHandle(long[].class, nativeOrder()).withInvokeExactBehavior();


  private final @NotNull MMappedFileStorage storage;
  /** Cached page(0) for faster access */
  private transient Page headerPage;


  /**
   * Incremented on each update of anything in the storage -- header, record. Hence be seen as 'version'
   * of storage content -- not a storage _format_ version, but current storage _content_.
   * Stored in {@link FileHeader#GLOBAL_MOD_COUNT_OFFSET} header field.
   * If a record is updated -> current value of globalModCount is 'stamped' into a record MOD_COUNT field.
   * <p>
   * In the current implementation almost all fields are read/write straight from/to the mmapped buffer -- which means
   * they are always 'saved', and no need to flush them explicitly => no need to update .dirty status. The only exception
   * is globalModCount which _should_ be flushed explicitly -- which is why difference between globalModCount and apt
   * header field {@link FileHeader#GLOBAL_MOD_COUNT_OFFSET} is used as sign of storage being 'dirty',
   * i.e. ask for {@link #force()}
   */
  private final AtomicInteger globalModCount = new AtomicInteger(0);
  //MAYBE RC: if we increment .globalModCount on _each_ modification -- this rises interesting possibility to
  //          detect corruptions without corruption marker: currently stored globalModCount (HEADER_GLOBAL_MOD_COUNT_OFFSET)
  //          is a version of last record what is guaranteed to be correctly written. So we could scan all
  //          records on file open, and ensure no one of them has .modCount > .globalModCount read from header.
  //          If this is true -- most likely records were stored correctly, even if app was crushed. If not, if
  //          we find a record(s) with modCount>globalModCount => there were writes unfinished on app crush, and
  //          likely at least those records are corrupted.

  //cached for faster access:
  private final transient int pageSize;
  private final transient int recordsPerPage;
  private final transient int recordsOnHeaderPage;

  private final transient HeaderAccessor headerAccessor = new HeaderAccessor(this);

  /**
   * Cached value {@link #maxAllocatedID()}.
   * fileId check against {@link #maxAllocatedID()} is very frequent, so it is worth to optimize it.
   * <p>
   * {@link #maxAllocatedID()} is always increasing, so we can cache last returned value, check against
   * it first, and only if fileId > lastMaxAllocatedId -- re-check against actual {@link #maxAllocatedID()}
   * value. This way most of checks should terminate early, on first check against simple field.
   * <p>
   * Thread-safety: field don't need to be volatile, since we have an invariant "if an id was valid at some
   * point, it is always valid since then" -- which means that if [id <= lastMaxAllocatedId] => id is valid,
   * regardless of how much outdated lastMaxAllocatedId value is. And if [id > lastMaxAllocatedId] => we'll
   * re-check against actual value anyway.
   */
  private int cachedMaxAllocatedId;

  private final boolean wasClosedProperly;

  private volatile int owningProcessId = 0;

  public PersistentFSRecordsLockFreeOverMMappedFile(@NotNull MMappedFileStorage storage) throws IOException {
    int pageSize = storage.pageSize();
    if (pageSize < FileHeader.HEADER_SIZE) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must fit header(=" + FileHeader.HEADER_SIZE + " b)");
    }
    this.storage = storage;

    this.pageSize = pageSize;
    recordsPerPage = pageSize / RecordLayout.RECORD_SIZE_IN_BYTES;
    recordsOnHeaderPage = (pageSize - FileHeader.HEADER_SIZE) / RecordLayout.RECORD_SIZE_IN_BYTES;

    headerPage = this.storage.pageByOffset(0);

    int modCount = getIntHeaderField(FileHeader.GLOBAL_MOD_COUNT_OFFSET);
    globalModCount.set(modCount);

    cachedMaxAllocatedId = maxAllocatedID();

    int ownerProcessId = getIntHeaderField(FileHeader.OWNER_PROCESS_ID_OFFSET);
    wasClosedProperly = (ownerProcessId == NULL_OWNER_PID);


    //MAYBE RC: We may try to 'fix' this error, i.e. invalidate all the fileIds that are found to be allocated beyond
    //          maxAllocatedID, and fill the region > maxAllocatedID with zeroes. But right now I believe the reason
    //          for the non-zero records in >maxAllocateID region is OS crash (IJPL-1016).
    //          If this is true, better to avoid trying to recover after such crashes since an attempt to recover is
    //          too risky: too many invariants are not guaranteed to hold after OS crash, and it is easy to leave VFS
    //          in an inconsistent state, while being sure everything is successfully recovered.
    //          Also, OS crashes are rare, so they are not worth the (significant) effort to recover from.
    int unAllocatedRecordsToCheck = wasClosedProperly ?
                                    UNALLOCATED_RECORDS_TO_CHECK_ZEROED_REGULAR :
                                    UNALLOCATED_RECORDS_TO_CHECK_ZEROED_CRASHED;
    if (unAllocatedRecordsToCheck > 0) {
      checkUnAllocatedRegionIsZeroed(unAllocatedRecordsToCheck);
    }
  }

  @Override
  public <R> R readRecord(int recordId,
                          @NotNull RecordReader<R> reader) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page, this);
    return reader.readRecord(recordAccessor);
  }

  @Override
  public int updateRecord(int recordId,
                          @NotNull RecordUpdater updater) throws IOException {
    int trueRecordId = (recordId <= NULL_ID) ?
                       allocateRecord() :
                       recordId;
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    //RC: hope EscapeAnalysis removes the allocation here:
    RecordAccessor recordAccessor = new RecordAccessor(recordId, recordOffsetOnPage, page, this);
    boolean updated = updater.updateRecord(recordAccessor);
    if (updated) {
      incrementRecordVersion(recordAccessor.pageBuffer, recordOffsetOnPage);
    }
    return trueRecordId;
  }

  @Override
  public <R> R readHeader(@NotNull HeaderReader<R> reader) throws IOException {
    return reader.readHeader(headerAccessor);
  }

  @Override
  public void updateHeader(@NotNull HeaderUpdater updater) throws IOException {
    if (updater.updateHeader(headerAccessor)) {
      incrementGlobalModCount();
    }
  }


  private static final class RecordAccessor implements RecordForUpdate {
    private final int recordId;
    private final int recordOffsetInPage;
    private final transient ByteBuffer pageBuffer;
    private final @NotNull PersistentFSRecordsLockFreeOverMMappedFile records;

    private RecordAccessor(int recordId,
                           int recordOffsetInPage,
                           Page recordPage,
                           @NotNull PersistentFSRecordsLockFreeOverMMappedFile records) {
      this.recordId = recordId;
      this.recordOffsetInPage = recordOffsetInPage;
      pageBuffer = recordPage.rawPageBuffer();
      this.records = records;
    }

    @Override
    public int recordId() {
      return recordId;
    }

    @Override
    public int getAttributeRecordId() {
      return getIntField(RecordLayout.ATTR_REF_OFFSET);
    }

    @Override
    public int getParent() {
      return getIntField(RecordLayout.PARENT_REF_OFFSET);
    }

    @Override
    public int getNameId() {
      return getIntField(RecordLayout.NAME_REF_OFFSET);
    }

    @Override
    public long getLength() {
      return getLongField(RecordLayout.LENGTH_OFFSET);
    }

    @Override
    public long getTimestamp() {
      return getLongField(RecordLayout.TIMESTAMP_OFFSET);
    }

    @Override
    public int getModCount() {
      return getIntField(RecordLayout.MOD_COUNT_OFFSET);
    }

    @Override
    public int getContentRecordId() {
      return getIntField(RecordLayout.CONTENT_REF_OFFSET);
    }

    @Override
    public @PersistentFS.Attributes int getFlags() {
      //noinspection MagicConstant
      return getIntField(RecordLayout.FLAGS_OFFSET);
    }

    @Override
    public void setAttributeRecordId(int attributeRecordId) {
      checkValidIdField(recordId, attributeRecordId, "attributeRecordId");
      setIntField(RecordLayout.ATTR_REF_OFFSET, attributeRecordId);
    }

    @Override
    public void setParent(int parentId) {
      records.checkParentIdIsValid(parentId);
      setIntField(RecordLayout.PARENT_REF_OFFSET, parentId);
    }

    @Override
    public void setNameId(int nameId) {
      checkValidIdField(recordId, nameId, "nameId");
      setIntField(RecordLayout.NAME_REF_OFFSET, nameId);
    }

    @Override
    public boolean setFlags(@PersistentFS.Attributes int flags) {
      return setIntFieldIfChanged(RecordLayout.FLAGS_OFFSET, flags);
    }

    @Override
    public boolean setLength(long length) {
      return setLongFieldIfChanged(RecordLayout.LENGTH_OFFSET, length);
    }

    @Override
    public boolean setTimestamp(long timestamp) {
      return setLongFieldIfChanged(RecordLayout.TIMESTAMP_OFFSET, timestamp);
    }

    @Override
    public boolean setContentRecordId(int contentRecordId) {
      checkValidIdField(recordId, contentRecordId, "contentRecordId");
      return setIntFieldIfChanged(RecordLayout.CONTENT_REF_OFFSET, contentRecordId);
    }


    private long getLongField(int fieldRelativeOffset) {
      return (long)LONG_HANDLE.getVolatile(pageBuffer, recordOffsetInPage + fieldRelativeOffset);
    }

    private boolean setLongFieldIfChanged(int fieldRelativeOffset,
                                          long newValue) {
      int fieldOffsetInPage = recordOffsetInPage + fieldRelativeOffset;
      long oldValue = (long)LONG_HANDLE.getVolatile(pageBuffer, fieldOffsetInPage);
      if (oldValue != newValue) {
        setLongVolatile(pageBuffer, fieldOffsetInPage, newValue);
        return true;
      }
      return false;
    }

    private int getIntField(int fieldRelativeOffset) {
      return (int)INT_HANDLE.getVolatile(pageBuffer, recordOffsetInPage + fieldRelativeOffset);
    }

    private void setIntField(int fieldRelativeOffset,
                             int newValue) {
      setIntVolatile(pageBuffer, recordOffsetInPage + fieldRelativeOffset, newValue);
    }

    private boolean setIntFieldIfChanged(int fieldRelativeOffset,
                                         int newValue) {
      int fieldOffsetInPage = recordOffsetInPage + fieldRelativeOffset;
      int oldValue = (int)INT_HANDLE.getVolatile(pageBuffer, fieldOffsetInPage);
      if (oldValue != newValue) {
        setIntVolatile(pageBuffer, fieldOffsetInPage, newValue);
        return true;
      }
      return false;
    }
  }

  private static final class HeaderAccessor implements HeaderForUpdate {
    private final @NotNull PersistentFSRecordsLockFreeOverMMappedFile records;

    private HeaderAccessor(@NotNull PersistentFSRecordsLockFreeOverMMappedFile records) { this.records = records; }

    @Override
    public long getTimestamp() {
      return records.getTimestamp();
    }

    @Override
    public int getVersion() throws IOException {
      return records.getVersion();
    }

    @Override
    public int getGlobalModCount() {
      return records.getGlobalModCount();
    }

    @Override
    public void setVersion(int version) {
      records.setVersion(version);
    }
  }

  // ==== records operations:  ================================================================ //

  @Override
  public int allocateRecord() throws IOException {
    Page headerPage = headerPage();
    ByteBuffer headerPageBuffer = headerPage.rawPageBuffer();
    while (true) {// CAS loop:
      int allocatedRecords = (int)INT_HANDLE.getVolatile(headerPageBuffer, FileHeader.RECORDS_ALLOCATED_OFFSET);
      int newAllocatedRecords = allocatedRecords + 1;
      if (INT_HANDLE.compareAndSet(headerPageBuffer, FileHeader.RECORDS_ALLOCATED_OFFSET, allocatedRecords, newAllocatedRecords)) {

        long recordOffsetInFile = recordOffsetInFile(newAllocatedRecords);
        int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
        Page page = storage.pageByOffset(recordOffsetInFile);
        ByteBuffer pageBuffer = page.rawPageBuffer();
        incrementRecordVersion(pageBuffer, recordOffsetOnPage);

        return newAllocatedRecords;
      }
    }
  }

  // 'one field at a time' operations

  @Override
  public void setAttributeRecordId(int recordId,
                                   int attributeRecordId) throws IOException {
    checkValidIdField(recordId, attributeRecordId, "attributeRecordId");
    setIntField(recordId, RecordLayout.ATTR_REF_OFFSET, attributeRecordId);
  }

  @Override
  public int getAttributeRecordId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.ATTR_REF_OFFSET);
  }

  @Override
  public int getParent(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.PARENT_REF_OFFSET);
  }

  @Override
  public void setParent(int recordId,
                        int parentId) throws IOException {
    checkParentIdIsValid(parentId);
    setIntField(recordId, RecordLayout.PARENT_REF_OFFSET, parentId);
  }

  @Override
  public int getNameId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.NAME_REF_OFFSET);
  }

  @Override
  public int updateNameId(int recordId,
                          int nameId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(nameId);
    return getAndSetIntField(recordId, RecordLayout.NAME_REF_OFFSET, nameId);
  }

  @Override
  public boolean setFlags(int recordId,
                          @PersistentFS.Attributes int newFlags) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();

    return setIntFieldIfChanged(pageBuffer, recordOffsetOnPage, RecordLayout.FLAGS_OFFSET, newFlags);
  }

  @Override
  public @PersistentFS.Attributes int getFlags(int recordId) throws IOException {
    //noinspection MagicConstant
    return getIntField(recordId, RecordLayout.FLAGS_OFFSET);
  }

  @Override
  public long getLength(int recordId) throws IOException {
    return getLongField(recordId, RecordLayout.LENGTH_OFFSET);
  }

  @Override
  public boolean setLength(int recordId,
                           long newLength) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    int fieldOffsetOnPage = recordOffsetOnPage + RecordLayout.LENGTH_OFFSET;
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    long storedLength = (long)LONG_HANDLE.getVolatile(pageBuffer, fieldOffsetOnPage);
    if (storedLength != newLength) {
      setLongVolatile(pageBuffer, fieldOffsetOnPage, newLength);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);

      return true;
    }
    return false;
  }


  @Override
  public long getTimestamp(int recordId) throws IOException {
    return getLongField(recordId, RecordLayout.TIMESTAMP_OFFSET);
  }

  @Override
  public boolean setTimestamp(int recordId,
                              long newTimestamp) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();

    return setLongFieldIfChanged(pageBuffer, recordOffsetOnPage, RecordLayout.TIMESTAMP_OFFSET, newTimestamp);
  }

  @Override
  public int getModCount(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.MOD_COUNT_OFFSET);
  }

  @Override
  public int getContentRecordId(int recordId) throws IOException {
    return getIntField(recordId, RecordLayout.CONTENT_REF_OFFSET);
  }

  @Override
  public boolean setContentRecordId(int recordId,
                                    int newContentRecordId) throws IOException {
    checkValidIdField(recordId, newContentRecordId, "contentRecordId");
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    return setIntFieldIfChanged(pageBuffer, recordOffsetOnPage, RecordLayout.CONTENT_REF_OFFSET, newContentRecordId);
  }

  @Override
  public void markRecordAsModified(int recordId) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    incrementRecordVersion(page.rawPageBuffer(), recordOffsetOnPage);
  }

  @Override
  public void cleanRecord(int recordId) throws IOException {
    checkRecordIdIsValid(recordId);

    //fill record with zeroes, by 4 bytes at once:
    assert RecordLayout.RECORD_SIZE_IN_BYTES % Integer.BYTES == 0
      : "RECORD_SIZE_IN_BYTES(=" + RecordLayout.RECORD_SIZE_IN_BYTES + ") is expected to be 32-aligned";

    int recordSizeInInts = RecordLayout.RECORD_SIZE_IN_BYTES / Integer.BYTES;

    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    for (int wordNo = 0; wordNo < recordSizeInInts; wordNo++) {
      int offsetOfWord = recordOffsetOnPage + wordNo * Integer.BYTES;
      setIntVolatile(pageBuffer, offsetOfWord, 0);
    }

    //make storage .dirty: usually it is done automatically, since we inc _global_ modCount while updating _record_ modCount,
    // but here we don't update record modCount, so must increment global one explicitly
    incrementGlobalModCount();
  }


  @Override
  public boolean processAllRecords(@NotNull PersistentFSRecordsStorage.FsRecordProcessor processor) throws IOException {
    int recordsCount = maxAllocatedID();
    for (int recordId = MIN_VALID_ID; recordId <= recordsCount; recordId++) {
      processor.process(
        recordId,
        getNameId(recordId),
        getFlags(recordId),
        getParent(recordId),
        getAttributeRecordId(recordId),
        getContentRecordId(recordId),
        /* corrupted = */ false
      );
    }
    return true;
  }


  // ============== storage 'global' properties accessors: ============================= //

  @Override
  public long getTimestamp() {
    return getLongHeaderField(FileHeader.CREATION_TIMESTAMP_OFFSET);
  }

  @Override
  public boolean wasClosedProperly() {
    return wasClosedProperly;
  }


  /**
   * Tries to acquire an exclusive ownership over the storage, for the process identified by acquiringProcessId.
   * Storages based on memory mapped files _could_ be used from >1 process concurrently -- but current implementation
   * of those storages is not designed for such a use -- and there is no legit scenarios of such a co-use for today.
   * Hence we need a way to protect the storage(s) from multi-process access. This method provides a way to acquire
   * 'ownership' of a storage for given process (identified by integer pid) -- implementation ensures that in a concurrent
   * settings only a single process could successfully acquire ownership (see below definition of 'successful').
   * <p>
   * BEWARE: storage provides a way to acquire ownership, but storage does NOT ensure that only the owner process
   * accesses it. It is the responsibility of the application to check (i.e. try acquiring) the ownership early,
   * before starting any other operations with the storage -- and step back if ownership is already acquired by
   * another process.
   *
   * @param acquiringProcessId must be != 0
   * @return OwnershipInfo(pid, timestamp) of the exclusive owner after the call.
   * If [returned info.pid == acquiringProcessId] => acquiring has succeeded, and acquiringProcessId is the new exclusive owner.
   * Otherwise: returned OwnershipInfo provides an information about who is the storage exclusive owner now.
   */
  public @NotNull OwnershipInfo tryAcquireExclusiveAccess(int acquiringProcessId,
                                                          long acquiringTimestampMs,
                                                          boolean forcibly) throws IOException {
    if (acquiringProcessId == NULL_OWNER_PID) {
      throw new IllegalArgumentException("acquiringPid(=" + acquiringProcessId + ") must be !=0");
    }
    ByteBuffer headerPageBuffer = headerPage().rawPageBuffer();
    while (true) {//CAS loop
      int currentOwnerProcessId = (int)INT_HANDLE.getVolatile(headerPageBuffer, FileHeader.OWNER_PROCESS_ID_OFFSET);
      if (currentOwnerProcessId == acquiringProcessId) {
        //already acquired => nothing to do
        owningProcessId = acquiringProcessId;
        long ownershipAcquiredMs = (long)LONG_HANDLE.getVolatile(headerPageBuffer, FileHeader.OWNERSHIP_ACQUIRED_TIMESTAMP_OFFSET);
        return new OwnershipInfo(acquiringProcessId, ownershipAcquiredMs);
      }
      if (currentOwnerProcessId != NULL_OWNER_PID && !forcibly) {
        //already acquired by other process => return owner's pid as an indication of failure
        long ownershipAcquiredMs = (long)LONG_HANDLE.getVolatile(headerPageBuffer, FileHeader.OWNERSHIP_ACQUIRED_TIMESTAMP_OFFSET);
        return new OwnershipInfo(currentOwnerProcessId, ownershipAcquiredMs);
      }
      if (INT_HANDLE.compareAndSet(headerPageBuffer, FileHeader.OWNER_PROCESS_ID_OFFSET, currentOwnerProcessId, acquiringProcessId)) {
        owningProcessId = acquiringProcessId;
        LONG_HANDLE.setVolatile(headerPageBuffer, FileHeader.OWNERSHIP_ACQUIRED_TIMESTAMP_OFFSET, acquiringTimestampMs);
        storage.fsync();//ensure status is persisted
        return new OwnershipInfo(acquiringProcessId, acquiringTimestampMs);
      }
    }
  }

  /**
   * Releases the ownership acquired by {@link #tryAcquireExclusiveAccess(int, long, boolean)}.
   * If {@code tryAcquireExclusiveAccess(pid)} has succeeded, than following {@code tryReleaseExclusiveAccess(pid)} must
   * also succeed (given nobody forcibly overwrites the ownership by calling {@link #tryAcquireExclusiveAccess(int, long, boolean)}
   * with forcible=true).
   *
   * @param ownerProcessId pid of current storage owner. Could be 0 if there is no current owner
   * @return pid of exclusive owner after the call.
   * If returned pid=0 => there is no owner, i.e. ownership was released successfully.
   * Otherwise: returned pid provides an information about who is currently owning the storage.
   * MAYBE RC: return OwnershipInfo, as in {@link #tryAcquireExclusiveAccess(int, long, boolean)}?
   */
  private int tryReleaseExclusiveAccess(int ownerProcessId) {
    ByteBuffer headerPageBuffer = headerPage().rawPageBuffer();
    while (true) {//CAS loop
      int currentOwnerProcessId = (int)INT_HANDLE.getVolatile(headerPageBuffer, FileHeader.OWNER_PROCESS_ID_OFFSET);
      if (currentOwnerProcessId == NULL_OWNER_PID) {
        return NULL_OWNER_PID;//nothing to do (method expected to be idempotent)
      }
      if (currentOwnerProcessId != ownerProcessId) {
        //acquired by another process => return its pid as an indication of failure
        return currentOwnerProcessId;
      }
      if (INT_HANDLE.compareAndSet(headerPageBuffer, FileHeader.OWNER_PROCESS_ID_OFFSET, currentOwnerProcessId, NULL_OWNER_PID)) {
        LONG_HANDLE.setVolatile(headerPageBuffer, FileHeader.OWNERSHIP_ACQUIRED_TIMESTAMP_OFFSET, 0L);
        return NULL_OWNER_PID;
      }
    }
  }

  @Override
  public int getErrorsAccumulated() {
    return getIntHeaderField(FileHeader.ERRORS_ACCUMULATED_OFFSET);
  }

  @Override
  public void setErrorsAccumulated(int errors) {
    setIntHeaderField(FileHeader.ERRORS_ACCUMULATED_OFFSET, errors);
    incrementGlobalModCount();
  }

  @Override
  public void setVersion(int version) {
    setIntHeaderField(FileHeader.VERSION_OFFSET, version);
    setLongHeaderField(FileHeader.CREATION_TIMESTAMP_OFFSET, System.currentTimeMillis());
    incrementGlobalModCount();
  }

  @Override
  public int getVersion() throws IOException {
    return getIntHeaderField(FileHeader.VERSION_OFFSET);
  }

  @Override
  public int getGlobalModCount() {
    return globalModCount.get();
  }

  @Override
  public int recordsCount() {
    return allocatedRecordsCount();
  }

  @Override
  public int maxAllocatedID() {
    //We assign id starting with 1 (id=0 is reserved as NULL_ID)
    // => (maxId == recordsCount)
    return allocatedRecordsCount();
  }

  @Override
  public boolean isValidFileId(int fileId) {
    if (fileId <= NULL_ID) {
      return false;
    }
    int cachedMaxAllocatedID = this.cachedMaxAllocatedId;
    if (fileId <= cachedMaxAllocatedID) {
      return true;
    }
    //'slow path' is extracted into dedicated method to reduce this method (=fast path) bytecode size,
    // and convince JIT inline it. Slow path inlining is not so important, since it is anyway slow.
    // Actually slow path is also inlined, but only after detected to be 'hot', which takes time.
    // So this 'optimization' mostly helps with speed on startup/warmup  -- which is important for us,
    // and especially with transition to jdk21, there JIT seems to have longer warmup before jumping
    // to full C2
    return isValidFileIdStrict(fileId);
  }

  private boolean isValidFileIdStrict(int fileId) {
    int actualMaxAllocatedID = maxAllocatedID();
    this.cachedMaxAllocatedId = Math.max(cachedMaxAllocatedId, actualMaxAllocatedID);
    if (fileId <= actualMaxAllocatedID) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isDirty() {
    return globalModCount.get() != getIntHeaderField(FileHeader.GLOBAL_MOD_COUNT_OFFSET);
  }

  @Override
  public void force() throws IOException {
    //store globalModCount in apt header field:
    ByteBuffer headerPageBuffer = headerPage().rawPageBuffer();
    while (true) {//CAS loop:
      int currentModCount = globalModCount.get();
      int storedModCount = (int)INT_HANDLE.getVolatile(headerPageBuffer, FileHeader.GLOBAL_MOD_COUNT_OFFSET);
      if (currentModCount <= storedModCount) {
        //Stored modCount is already ahead => we're quite late, someone else is already stored 'fresher' value
        //(modCount always increases => larger value implies 'later' in happens-before sequence)
        break;
      }
      if (INT_HANDLE.compareAndSet(headerPageBuffer, FileHeader.GLOBAL_MOD_COUNT_OFFSET, storedModCount, currentModCount)) {
        break;
      }
    }

    if (MMappedFileStorage.FSYNC_ON_FLUSH_BY_DEFAULT) {
      storage.fsync();
    }
  }

  @Override
  public void close() throws IOException {
    if (storage.isOpen()) {
      int ourPid = owningProcessId;
      int currentOwnerPid = tryReleaseExclusiveAccess(ourPid);

      force();
      storage.close();

      headerPage = null;

      if (currentOwnerPid != NULL_OWNER_PID) {
        //important to NOT throw an exception here -- close must be successful regardless of success of ownership acquiring
        FSRecords.LOG.warn("Storage is exclusively owned by another process[pid: " + currentOwnerPid + ", our pid: " + ourPid + "]");
      }
    }
  }

  @Override
  public void closeAndUnsafelyUnmap() throws IOException {
    close();
    storage.closeAndUnsafelyUnmap();
  }

  /** Close the storage and remove all its data files */
  @Override
  public void closeAndClean() throws IOException {
    close();
    storage.closeAndClean();
  }

  // =============== implementation: addressing ========================================================= //

  /** Without recordId bounds checking */
  @VisibleForTesting
  public long recordOffsetInFileUnchecked(int recordId) {
    //recordId is 1-based, convert to 0-based recordNo:
    int recordNo = recordId - 1;

    if (recordNo < recordsOnHeaderPage) {
      return FileHeader.HEADER_SIZE + recordNo * (long)RecordLayout.RECORD_SIZE_IN_BYTES;
    }

    //as-if there were no header:
    int fullPages = recordNo / recordsPerPage;
    int recordsOnLastPage = recordNo % recordsPerPage;

    //header on the first page "pushes out" few records:
    int recordsExcessBecauseOfHeader = recordsPerPage - recordsOnHeaderPage;

    //so the last page could turn into +1 page:
    int recordsReallyOnLastPage = recordsOnLastPage + recordsExcessBecauseOfHeader;
    return (long)(fullPages + recordsReallyOnLastPage / recordsPerPage) * pageSize
           + (long)(recordsReallyOnLastPage % recordsPerPage) * RecordLayout.RECORD_SIZE_IN_BYTES;
  }

  private long recordOffsetInFile(int recordId) throws IndexOutOfBoundsException {
    checkRecordIdIsValid(recordId);
    return recordOffsetInFileUnchecked(recordId);
  }


  private void checkRecordIdIsValid(int recordId) throws IndexOutOfBoundsException {
    if (!isValidFileId(recordId)) {
      throw new IndexOutOfBoundsException(
        "recordId(=" + recordId + ") is outside of allocated IDs range (0, " + maxAllocatedID() + "]");
    }
  }

  private void checkParentIdIsValid(int parentId) throws IndexOutOfBoundsException {
    if (parentId == NULL_ID) {
      //parentId could be NULL (for root records) -- this is the difference with checkRecordIdIsValid()
      return;
    }
    if (!isValidFileId(parentId)) {
      throw new IndexOutOfBoundsException(
        "parentId(=" + parentId + ") is outside of allocated IDs range [0, " + maxAllocatedID() + "]");
    }
  }

  private static void checkValidIdField(int recordId,
                                        int idFieldValue,
                                        @NotNull String fieldName) {
    if (idFieldValue < NULL_ID) {
      throw new IllegalArgumentException("file[id: " + recordId + "]." + fieldName + "(=" + idFieldValue + ") must be >=0");
    }
  }

  // =============== implementation: record field access ================================================ //

  /**
   * How many records were allocated already. Since id=0 is reserved (NULL_ID), we start assigning ids from 1,
   * and hence (last record id == allocatedRecordsCount)
   */
  private int allocatedRecordsCount() {
    return getIntHeaderField(FileHeader.RECORDS_ALLOCATED_OFFSET);
  }

  private void setLongField(int recordId,
                            @FieldOffset int fieldRelativeOffset,
                            long fieldValue) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    setLongVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset, fieldValue);
    incrementRecordVersion(pageBuffer, recordOffsetOnPage);
  }

  private long getLongField(int recordId,
                            @FieldOffset int fieldRelativeOffset) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    return (long)LONG_HANDLE.getVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset);
  }

  private boolean setLongFieldIfChanged(ByteBuffer pageBuffer,
                                        int recordOffsetOnPage,
                                        @FieldOffset int fieldRelativeOffset,
                                        long newValue) {
    int fieldOffsetOnPage = recordOffsetOnPage + fieldRelativeOffset;
    long oldValue = (long)LONG_HANDLE.getVolatile(pageBuffer, fieldOffsetOnPage);
    if (oldValue != newValue) {
      setLongVolatile(pageBuffer, fieldOffsetOnPage, newValue);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);
      return true;
    }
    return false;
  }


  private void setIntField(int recordId,
                           @FieldOffset int fieldRelativeOffset,
                           int fieldValue) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    setIntVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset, fieldValue);
    incrementRecordVersion(pageBuffer, recordOffsetOnPage);
  }

  private int getAndSetIntField(int recordId,
                                @FieldOffset int fieldRelativeOffset,
                                int fieldValue) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    int previousValue = getAndSetIntVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset, fieldValue);
    incrementRecordVersion(pageBuffer, recordOffsetOnPage);
    return previousValue;
  }

  private int getIntField(int recordId,
                          @FieldOffset int fieldRelativeOffset) throws IOException {
    long recordOffsetInFile = recordOffsetInFile(recordId);
    int recordOffsetOnPage = storage.toOffsetInPage(recordOffsetInFile);
    Page page = storage.pageByOffset(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    return (int)INT_HANDLE.getVolatile(pageBuffer, recordOffsetOnPage + fieldRelativeOffset);
  }

  private boolean setIntFieldIfChanged(ByteBuffer pageBuffer,
                                       int recordOffsetOnPage,
                                       int fieldRelativeOffset,
                                       int newValue) {
    int fieldOffsetOnPage = recordOffsetOnPage + fieldRelativeOffset;
    int oldValue = (int)INT_HANDLE.getVolatile(pageBuffer, fieldOffsetOnPage);
    if (oldValue != newValue) {
      setIntVolatile(pageBuffer, fieldOffsetOnPage, newValue);
      incrementRecordVersion(pageBuffer, recordOffsetOnPage);

      return true;
    }
    return false;
  }


  private void incrementRecordVersion(@NotNull ByteBuffer pageBuffer,
                                      int recordOffsetOnPage) {
    int globalModCount = incrementGlobalModCount();
    setIntVolatile(pageBuffer, recordOffsetOnPage + RecordLayout.MOD_COUNT_OFFSET, globalModCount);
  }

  private int incrementGlobalModCount() {
    int modCount = globalModCount.incrementAndGet();
    if (modCount < 0) {
      throw new IllegalStateException("GlobalModCount(=" + modCount + ") is negative: overflow?");
    }
    return modCount;
  }

  //============ header fields access: ============================================================ //

  private void setLongHeaderField(int headerRelativeOffsetBytes,
                                  long headerValue) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    setLongVolatile(headerPage().rawPageBuffer(), headerRelativeOffsetBytes, headerValue);
  }

  private long getLongHeaderField(int headerRelativeOffsetBytes) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    return (long)LONG_HANDLE.getVolatile(headerPage().rawPageBuffer(), headerRelativeOffsetBytes);
  }

  private void setIntHeaderField(int headerRelativeOffsetBytes,
                                 int headerValue) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    setIntVolatile(headerPage().rawPageBuffer(), headerRelativeOffsetBytes, headerValue);
  }


  private int getIntHeaderField(int headerRelativeOffsetBytes) {
    checkHeaderOffset(headerRelativeOffsetBytes);
    return (int)INT_HANDLE.getVolatile(headerPage().rawPageBuffer(), headerRelativeOffsetBytes);
  }

  private Page headerPage() {
    Page page = headerPage;
    if (page == null) {
      throw new AlreadyDisposedException("File records storage is already closed");
    }
    return page;
  }

  private static void checkHeaderOffset(int headerRelativeOffset) {
    if (!(0 <= headerRelativeOffset && headerRelativeOffset < FileHeader.HEADER_SIZE)) {
      throw new IndexOutOfBoundsException(
        "headerFieldOffset(=" + headerRelativeOffset + ") is outside of header [0, " + FileHeader.HEADER_SIZE + ") ");
    }
  }


  private static void setIntVolatile(ByteBuffer pageBuffer,
                                     int offsetInBuffer,
                                     int value) {
    INT_HANDLE.setVolatile(pageBuffer, offsetInBuffer, value);
  }

  private static int getAndSetIntVolatile(ByteBuffer pageBuffer,
                                          int offsetInBuffer,
                                          int value) {
    return (int)INT_HANDLE.getAndSet(pageBuffer, offsetInBuffer, value);
  }

  private static void setLongVolatile(ByteBuffer pageBuffer,
                                      int offsetInBuffer,
                                      long value) {
    LONG_HANDLE.setVolatile(pageBuffer, offsetInBuffer, value);
  }

  // ========================== debug/diagnostics ========================================================= //

  private void checkUnAllocatedRegionIsZeroed(int recordsToCheck) throws IOException {
    int maxAllocatedID = maxAllocatedID();
    int firstUnAllocatedId = maxAllocatedID + 1;
    long unallocatedRegionStartingOffsetInFile = recordOffsetInFileUnchecked(firstUnAllocatedId);
    int unallocatedRegionStartingOffsetOnPage = storage.toOffsetInPage(unallocatedRegionStartingOffsetInFile);
    long actualFileSize = storage.actualFileSize();
    if (unallocatedRegionStartingOffsetOnPage >= actualFileSize) {
      return;//un-allocated file region is definitely empty
    }

    Page lastPage = storage.pageByOffset(unallocatedRegionStartingOffsetInFile);
    ByteBuffer lastPageBuffer = lastPage.rawPageBuffer();

    int maxBytesRemainsOnPage = lastPageBuffer.limit() - unallocatedRegionStartingOffsetOnPage;
    int bytesToCheck = Math.min(
      recordsToCheck * RecordLayout.RECORD_SIZE_IN_BYTES,
      maxBytesRemainsOnPage
    );

    int firstNonZeroOffsetInPage = firstNonZeroByteOffset(lastPageBuffer, unallocatedRegionStartingOffsetOnPage, bytesToCheck);
    if (firstNonZeroOffsetInPage >= 0) {
      //if we already found non-0 record, no reason for economy:
      // => better to collect AMAP diagnostic info
      // => lets check more bytes (but not too many: i.e. scanning 64Mb byte-by-byte could be quite offensive for UX,
      // and for our TeamCity tests as well!)
      int bytesToCheckAdditionally = Math.min(maxBytesRemainsOnPage, 1 << 16);
      int lastNonZeroOffsetInPage = lastNonZeroByteOffset(lastPageBuffer, unallocatedRegionStartingOffsetOnPage, bytesToCheckAdditionally);
      int nonZeroBytesBeyondEOF = lastNonZeroOffsetInPage - unallocatedRegionStartingOffsetOnPage + 1;
      int nonZeroedRecordsCount = (nonZeroBytesBeyondEOF / RecordLayout.RECORD_SIZE_IN_BYTES) + 1;

      throw new CorruptedException(
        "Non-empty records detected beyond current EOF => storage is corrupted.\n" +
        "\tmax allocated id(=" + maxAllocatedID + ")\n" +
        "\tfirst un-allocated offset: " + unallocatedRegionStartingOffsetInFile + "\n" +
        "\tcontent beyond allocated region(" + recordsToCheck + " records max): \n" +
        dumpRecordsAsHex(firstUnAllocatedId, firstUnAllocatedId + recordsToCheck) + "\n" +
        "=" + nonZeroedRecordsCount + " total non-zero records on the page, in range " +
        "[" + unallocatedRegionStartingOffsetInFile + ".." + (unallocatedRegionStartingOffsetInFile + nonZeroBytesBeyondEOF) + "), " +
        "wasClosedProperly=" + wasClosedProperly
      );
    }
  }

  /**
   * @return offset of the first non-zero byte in a range [startingOffset..startingOffset+maxBytesToCheck),
   * or -1 if all bytes in the range are 0
   */
  private static int firstNonZeroByteOffset(@NotNull ByteBuffer buffer,
                                            int startingOffset,
                                            int maxBytesToCheck) {
    for (int i = 0; i < maxBytesToCheck; i++) {
      byte b = buffer.get(startingOffset + i);
      if (b != 0) {
        return startingOffset + i;
      }
    }
    return -1;
  }

  /**
   * @return offset of the last non-zero byte in a range [startingOffset..startingOffset+maxBytesToCheck),
   * or -1 if all bytes in the range are 0
   */
  private static int lastNonZeroByteOffset(@NotNull ByteBuffer buffer,
                                           int startingOffset,
                                           int maxBytesToCheck) {
    int lastNonZeroOffset = -1;
    for (int i = 0; i < maxBytesToCheck; i++) {
      byte b = buffer.get(startingOffset + i);
      if (b != 0) {
        lastNonZeroOffset = startingOffset + i;
      }
    }
    return lastNonZeroOffset;
  }

  /**
   * Method is for debugging/monitoring purposes
   *
   * @return records [firstRecordId..lastRecordId] (both ends inclusive) hex-formatted, one per line
   */
  public String dumpRecordsAsHex(int firstRecordId,
                                 int lastRecordId) throws IOException {
    if (firstRecordId > lastRecordId) {
      return "<no records in range " + firstRecordId + " .. " + lastRecordId + ">";
    }
    long actualFileSize = storage.actualFileSize();
    StringBuilder sb = new StringBuilder();
    for (int recordId = firstRecordId; recordId <= lastRecordId; recordId++) {
      String recordAsHex;
      if (recordId == NULL_ID) {
        recordAsHex = "<header>";
      }
      else {
        long recordOffsetInFile = recordOffsetInFileUnchecked(recordId);

        if (recordOffsetInFile >= actualFileSize) {
          recordAsHex = "<EOF: outside of allocated file region>";
        }
        else {
          int recordOffsetInPage = storage.toOffsetInPage(recordOffsetInFile);

          Page page = storage.pageByOffset(recordOffsetInFile);
          ByteBuffer pageBuffer = page.rawPageBuffer();
          ByteBuffer recordSlice = pageBuffer.slice(recordOffsetInPage, RecordLayout.RECORD_SIZE_IN_BYTES);

          recordAsHex = IOUtil.toHexString(recordSlice);
        }
      }
      sb.append("[#%06d/max=%06d]: ".formatted(recordId, maxAllocatedID()))
        .append(recordAsHex)
        .append('\n');
    }
    return sb.toString();
  }

  @MagicConstant(flagsFromClass = RecordLayout.class)
  @Target(ElementType.TYPE_USE)
  public @interface FieldOffset {
  }

  public static final class OwnershipInfo {
    public final int ownerProcessPid;
    public final long ownershipAcquiredAtMs;

    public OwnershipInfo(int ownerProcessPid,
                         long ownershipAcquiredAtMs) {
      if (ownerProcessPid < 0) {
        throw new IllegalArgumentException("pid(=" + ownerProcessPid + ") must be positive");
      }
      this.ownerProcessPid = ownerProcessPid;
      this.ownershipAcquiredAtMs = ownershipAcquiredAtMs;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      OwnershipInfo info = (OwnershipInfo)o;
      return ownerProcessPid == info.ownerProcessPid && ownershipAcquiredAtMs == info.ownershipAcquiredAtMs;
    }

    @Override
    public int hashCode() {
      int result = ownerProcessPid;
      result = 31 * result + Long.hashCode(ownershipAcquiredAtMs);
      return result;
    }

    @Override
    public String toString() {
      return "OwnershipInfo{owner pid: " + ownerProcessPid + ", acquired at: " + ownershipAcquiredAtMs + '}';
    }
  }
}
