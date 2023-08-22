// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.appendonlylog;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile.MMappedFileStorage.Page;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferReader;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.ByteBufferWriter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.io.IOUtil.MiB;
import static java.lang.invoke.MethodHandles.byteBufferViewVarHandle;
import static java.nio.ByteOrder.nativeOrder;

/**
 * There are other caveats, pitfalls, and dragons, so beware
 */
@ApiStatus.Internal
public class AppendOnlyLogOverMMappedFile implements AppendOnlyLog {
  private static final VarHandle INT32_OVER_BYTE_BUFFER = byteBufferViewVarHandle(int[].class, nativeOrder()).withInvokeExactBehavior();
  private static final VarHandle INT64_OVER_BYTE_BUFFER = byteBufferViewVarHandle(long[].class, nativeOrder()).withInvokeExactBehavior();

  /** We assume the mapped file is filled with 0 initially, so 0 for any field is the value before anything was set */
  private static final int UNSET_VALUE = 0;

  private static final int CURRENT_IMPLEMENTATION_VERSION = 1;

  public static final int DEFAULT_PAGE_SIZE = 4 * MiB;

  //TODO/MAYBE:
  //    1) connectionStatus: do we need it? Updates are atomic, every saved state is at least self-consistent.
  //       We could use (committed < allocated) as a marker of 'not everything was committed'
  //    2) Align records to 32b -- required to for setVolatile(RecordLayout.LENGTH_OFFSET), without it multithreaded
  //       use is unsafe
  //    3) Flush cursors values to header fields more often: it is crucial for the 'recoverability' -- only the section
  //       of the log until NEXT_RECORD_TO_BE_COMMITTED_OFFSET is recoverable after a crash.
  //       Maybe get rid of atomic fields, and update cursors plainly in a header?
  //    4) Cleanup-recovery after crash: if (committed < allocated) on start -- we need to set [allocated=committed],
  //       and zeroes (committed..allocated) region (since it could contain partially-saved garbage, while we rely
  //       on 'unwritten' buffer content to be 0). Probably we need to zero even more than that region alone --
  //       because we don't know how many writes ahead of it were saved.

  private static class HeaderLayout {
    private static final int IMPLEMENTATION_VERSION_OFFSET = 0;

    private static final int EXTERNAL_VERSION_OFFSET = IMPLEMENTATION_VERSION_OFFSET + Integer.BYTES;


    private static final int NEXT_RECORD_TO_BE_ALLOCATED_OFFSET = EXTERNAL_VERSION_OFFSET + Integer.BYTES;
    /** Records until (<) that offset must be all written */
    private static final int NEXT_RECORD_TO_BE_COMMITTED_OFFSET = NEXT_RECORD_TO_BE_ALLOCATED_OFFSET + Long.BYTES;

    //reserve [8 x int64] just for the case
    private static final int HEADER_SIZE = NEXT_RECORD_TO_BE_COMMITTED_OFFSET + 8 * Long.BYTES;
  }

  private static class RecordLayout {

    /** length is >0 for real records, -length is stored for padding records */
    private static final int LENGTH_OFFSET = 0;
    private static final int DATA_OFFSET = LENGTH_OFFSET + Integer.BYTES;

    private static final int RECORD_HEADER_SIZE = Integer.BYTES;

    private static void putDataRecord(@NotNull ByteBuffer buffer,
                                      int offsetInBuffer,
                                      byte[] data) {
      int totalRecordSize = data.length + RECORD_HEADER_SIZE;
      buffer.put(offsetInBuffer + DATA_OFFSET, data);
      //INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + LENGTH_OFFSET, totalRecordSize);
      buffer.putInt(offsetInBuffer + LENGTH_OFFSET, totalRecordSize);
    }

    private static void putDataRecord(@NotNull ByteBuffer buffer,
                                      int offsetInBuffer,
                                      int recordSize,
                                      ByteBufferWriter writer) throws IOException {
      int totalRecordSize = recordSize + RECORD_HEADER_SIZE;
      writer.write(buffer.slice(offsetInBuffer + DATA_OFFSET, recordSize));
      //INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + LENGTH_OFFSET, totalRecordSize);
      buffer.putInt(offsetInBuffer + LENGTH_OFFSET, totalRecordSize);
    }

    private static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                         int offsetInBuffer) {
      int remainsToPad = buffer.capacity() - offsetInBuffer;
      if (remainsToPad <= RECORD_HEADER_SIZE) {
        throw new IllegalArgumentException(
          "Can't create PaddingRecord for " + remainsToPad + "b leftover, must be >" + RECORD_HEADER_SIZE + " b left:" +
          "buffer.capacity(=" + buffer.capacity() + "), offsetInBuffer(=" + offsetInBuffer + ")");
      }
      //INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + LENGTH_OFFSET, -remainsToPad);
      buffer.putInt(offsetInBuffer + LENGTH_OFFSET, -remainsToPad);
    }

    /** @return total record length (including header) */
    private static int recordLength(ByteBuffer buffer,
                                    int offsetInBuffer) {
      return Math.abs(buffer.getInt(offsetInBuffer + LENGTH_OFFSET));
    }

    private static boolean isPaddingRecord(ByteBuffer buffer,
                                           int offsetInBuffer) {
      return buffer.getInt(offsetInBuffer + LENGTH_OFFSET) < 0;
    }

    private static boolean isDataRecord(ByteBuffer buffer,
                                        int offsetInBuffer) {
      return !isPaddingRecord(buffer, offsetInBuffer);
    }

    private static ByteBuffer recordDataAsSlice(ByteBuffer buffer,
                                                int offsetInBuffer) {
      int totalRecordLength = buffer.getInt(offsetInBuffer + LENGTH_OFFSET);
      if (totalRecordLength < 0) {
        throw new IllegalStateException("record[" + offsetInBuffer + "] is " +
                                        "PaddingRecord(length=" + totalRecordLength + ") -- has no data");
      }
      else if (totalRecordLength == 0) {
        throw new IllegalStateException("record[" + offsetInBuffer + "] has no length(=" + totalRecordLength + ") " +
                                        "either not yet written or corrupted");
      }
      return buffer.slice(offsetInBuffer + DATA_OFFSET, totalRecordLength - RECORD_HEADER_SIZE);
    }
  }


  //public static @NotNull AppendOnlyLogOverMMappedFile openOrCreate(@NotNull FSRecordsImpl vfs,
  //                                                                 @NotNull String name) throws IOException {
  //  Path fastAttributesDir = vfs.storagesPaths().storagesSubDir("extended-attributes");
  //  Files.createDirectories(fastAttributesDir);
  //
  //  Path storagePath = fastAttributesDir.resolve(name);
  //  MMappedFileStorage storage = new MMappedFileStorage(storagePath, DEFAULT_PAGE_SIZE);
  //  return new AppendOnlyLogOverMMappedFile(storage);
  //}


  private final @NotNull MMappedFileStorage storage;

  /** Offset (in file) of the next-record-to-be-allocated */
  private final AtomicLong nextRecordToBeAllocatedOffset = new AtomicLong();
  /**
   * Offset (in file) of the next-record-to-be-allocated.
   * Records with offset < recordsCommittedUpToOffset are guaranteed to be already written.
   */
  private final AtomicLong nextRecordToBeCommittedOffset = new AtomicLong();


  public AppendOnlyLogOverMMappedFile(@NotNull MMappedFileStorage storage) throws IOException {
    this.storage = storage;

    int implementationVersion = getImplementationVersion();
    if (implementationVersion == UNSET_VALUE) {
      setImplementationVersion(CURRENT_IMPLEMENTATION_VERSION);
    }
    else if (implementationVersion != CURRENT_IMPLEMENTATION_VERSION) {
      throw new IOException("Storage .implementationVersion(=" + implementationVersion + ") is not supported: " +
                            CURRENT_IMPLEMENTATION_VERSION + " is the supported one.");
    }

    long nextRecordToBeAllocatedOffset = getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET);
    if (nextRecordToBeAllocatedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeAllocatedOffset = HeaderLayout.HEADER_SIZE;
      setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset);
    }
    this.nextRecordToBeAllocatedOffset.set(nextRecordToBeAllocatedOffset);

    long nextRecordToBeCommittedOffset = getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET);
    if (nextRecordToBeCommittedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeCommittedOffset = HeaderLayout.HEADER_SIZE;
      setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
    }
    this.nextRecordToBeCommittedOffset.set(nextRecordToBeAllocatedOffset);
  }

  public int getImplementationVersion() throws IOException {
    return getIntHeaderField(HeaderLayout.IMPLEMENTATION_VERSION_OFFSET);
  }

  private void setImplementationVersion(int implementationVersion) throws IOException {
    setIntHeaderField(HeaderLayout.IMPLEMENTATION_VERSION_OFFSET, implementationVersion);
  }

  public int getDataVersion() throws IOException {
    return getIntHeaderField(HeaderLayout.EXTERNAL_VERSION_OFFSET);
  }

  public void setDataVersion(int version) throws IOException {
    setIntHeaderField(HeaderLayout.EXTERNAL_VERSION_OFFSET, version);
  }

  public Path storagePath() {
    return storage.storagePath();
  }

  @Override
  public long append(@NotNull ByteBufferWriter writer,
                     int recordSize) throws IOException {
    if (recordSize < 0) {
      throw new IllegalArgumentException("Can't write record with length<0");
    }
    int totalRecordLength = recordSize + RecordLayout.RECORD_HEADER_SIZE;
    long recordOffsetInFile = allocateSpaceForRecord(totalRecordLength);

    Page page = storage.pageByOffset(recordOffsetInFile);
    int offsetInPage = storage.toOffsetInPage(recordOffsetInFile);
    RecordLayout.putDataRecord(page.rawPageBuffer(), offsetInPage, recordSize, writer);

    tryCommitRecord(recordOffsetInFile, totalRecordLength);

    return recordOffsetToId(recordOffsetInFile);
  }

  //MAYBE RC: Implementation below is a bit faster than default version (no lambda, no buffer slice).
  //          Dut does it worth it?
  //@Override
  //public long append(byte[] data) throws IOException {
  //  if (data.length == 0) {
  //    throw new IllegalArgumentException("Can't write record with length=0");
  //  }
  //  int totalRecordLength = data.length + RecordLayout.RECORD_HEADER_SIZE;
  //  long recordOffsetInFile = allocateSpaceForRecord(totalRecordLength);
  //
  //  Page page = storage.pageByOffset(recordOffsetInFile);
  //  int offsetInPage = storage.toOffsetInPage(recordOffsetInFile);
  //  RecordLayout.putDataRecord(page.rawPageBuffer(), offsetInPage, data);
  //
  //  tryCommitRecord(recordOffsetInFile, totalRecordLength);
  //
  //  return recordOffsetToId(recordOffsetInFile);
  //}

  @Override
  public <T> T read(long recordId,
                    @NotNull ByteBufferReader<T> reader) throws IOException {
    long recordOffsetInFile = recordIdToOffset(recordId);

    //FIXME RC: if we check offset against 'committed' cursor, we prohibit reading just-written
    //          record if there are uncommitted records behind it. This is bad from API-consistency
    //          PoV: if append() returns an id -- that id must be valid for read(id). So I changed
    //          check to ( offset < allocated ).
    //          ...But that creates another consistency issue then: .forEachRecord() unable to read
    //          records above 'committed' cursor since it relies on .recordLength in a header, and
    //          some headers in uncommitted region are not committed yet. So some records that could
    //          be accessed by id -- won't be reported by .forEachRecord(), which is also bad.
    //          ...Ideally, we should reconsider record writing/committing protocol: recordLength
    //          should be separated from 'committed/uncommitted' mark -- recordLength is written
    //          initially, before record content, but with 'uncommitted' mark => the record content
    //          can't be read yet, but the record could be traversed through.

    long recordsAllocatedUpTo = nextRecordToBeAllocatedOffset.get();
    if (recordOffsetInFile >= recordsAllocatedUpTo) {
      throw new IllegalArgumentException(
        "Can't read recordId(=" + recordId + ", offset: " + recordOffsetInFile + "]: " +
        "outside of allocated region [<" + recordsAllocatedUpTo + "]");
    }


    Page page = storage.pageByOffset(recordOffsetInFile);
    int recordOffsetInPage = storage.toOffsetInPage(recordOffsetInFile);
    ByteBuffer pageBuffer = page.rawPageBuffer();
    ByteBuffer recordDataSlice = RecordLayout.recordDataAsSlice(pageBuffer, recordOffsetInPage);
    return reader.read(recordDataSlice);
  }

  @Override
  public boolean forEachRecord(@NotNull RecordReader reader) throws IOException {
    int pageSize = storage.pageSize();
    for (long offset = HeaderLayout.HEADER_SIZE; offset < nextRecordToBeCommittedOffset.get(); ) {
      Page page = storage.pageByOffset(offset);
      int recordOffsetInPage = storage.toOffsetInPage(offset);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - recordOffsetInPage <= RecordLayout.RECORD_HEADER_SIZE) {
        offset += (pageSize - recordOffsetInPage);
        continue;
      }

      if (RecordLayout.isDataRecord(pageBuffer, recordOffsetInPage)) {
        ByteBuffer recordDataSlice = RecordLayout.recordDataAsSlice(pageBuffer, recordOffsetInPage);
        int dataLength = recordDataSlice.remaining();
        long recordId = recordOffsetToId(offset);
        boolean shouldContinue = reader.read(recordId, recordDataSlice);
        if (!shouldContinue) {
          return false;
        }
        offset += dataLength + RecordLayout.RECORD_HEADER_SIZE;
      }
      else if (RecordLayout.isPaddingRecord(pageBuffer, recordOffsetInPage)) {
        int paddingRecordLength = RecordLayout.recordLength(pageBuffer, recordOffsetInPage);
        offset += paddingRecordLength;
      }
      else {
        //before 'committed' cursor all records must be finalized -- i.e. header != 0
        throw new AssertionError("offset(" + offset + "): nor padding, nor data record");
      }
    }

    //MAYBE: there could be some finalized records in [committed, allocated), but with current scheme
    // it is impossible to know there such record(s) starts
    return true;
  }

  public void clear() throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  public void flush() throws IOException {
    flush(false);
  }

  public void flush(boolean fsync) throws IOException {
    setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_ALLOCATED_OFFSET, nextRecordToBeAllocatedOffset.get());
    setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset.get());
    if (fsync) {
      storage.fsync();
    }
  }

  /**
   * Closes the file, releases the mapped buffers, and tries to delete the file.
   * <p/>
   * Implementation note: the exact moment file memory-mapping is actually released and the file could be
   * deleted -- is very OS/platform-dependent. E.g., Win is known to keep file 'in use' for some time even
   * after unmap() call is already finished. In JVM GS is responsible for releasing mapped buffers -- which
   * adds another level of uncertainty. Hence, if one needs to re-create the storage, it may be more reliable
   * to just .clear() the current storage, than to closeAndRemove -> create-fresh-new.
   */
  public void closeAndRemove() throws IOException {
    close();
    FileUtil.delete(storage.storagePath());
  }

  @Override
  public void close() throws IOException {
    //MAYBE RC: is it better to always fsync here? -- or better state that flush(true) should be called
    //          explicitly, if needed, otherwise leave it to OS to decide when to sync the pages?
    flush();
    storage.close();
  }

  @Override
  public String toString() {
    return "AppendOnlyLogOverMMappedFile[" + storage.storagePath() + "]";
  }


  // ============== implementation: ======================================================================

  private long allocateSpaceForRecord(int totalRecordLength) throws IOException {
    int pageSize = storage.pageSize();
    while (true) {//CAS loop:
      long recordOffsetInFile = nextRecordToBeAllocatedOffset.get();
      int recordOffsetInPage = storage.toOffsetInPage(recordOffsetInFile);
      int remainingOnPage = pageSize - recordOffsetInPage;
      if (totalRecordLength <= remainingOnPage) {
        if (nextRecordToBeAllocatedOffset.compareAndSet(recordOffsetInFile, recordOffsetInFile + totalRecordLength)) {
          return recordOffsetInFile;
        }
        continue;
      }
      //not enough room on page for a record => fill page up with padding record
      // and try again on the next page:
      if (remainingOnPage > RecordLayout.RECORD_HEADER_SIZE) {
        if (nextRecordToBeAllocatedOffset.compareAndSet(recordOffsetInFile, recordOffsetInFile + remainingOnPage)) {
          Page page = storage.pageByOffset(recordOffsetInFile);
          RecordLayout.putPaddingRecord(page.rawPageBuffer(), recordOffsetInPage);
        }//else: somebody else dealt with that offset -> retry anyway
        continue;
      }

      //remainingOnPage <= RECORD_HEADER_SIZE: even padding record can't fit
      // => just move the pointer to the next page:
      //TODO RC: if we align records on 32b -- this branch becomes unreachable?
      long nextPageStartOffset = nextPageStartingOffset(recordOffsetInFile, pageSize);
      nextRecordToBeAllocatedOffset.compareAndSet(recordOffsetInFile, nextPageStartOffset);
      //don't need to check return value: either we succeed, or somebody else does
    }
  }

  private void tryCommitRecord(long currentRecordOffsetInFile,
                               int totalRecordLength) throws IOException {
    long committedOffset = nextRecordToBeCommittedOffset.get();
    if (committedOffset == currentRecordOffsetInFile) {
      long nextRecordOffsetInFile = nextRecordOffset(currentRecordOffsetInFile, totalRecordLength);
      if (nextRecordToBeCommittedOffset.compareAndSet(currentRecordOffsetInFile, nextRecordOffsetInFile)) {
        //Now we're responsible for moving the 'committed' pointer as much forward as possible:
        tryCommitFinalizedRecords();
      }
    }

    if (committedOffset < currentRecordOffsetInFile) {
      //some records before us are not yet committed

      //FIXME: Ideally, we shouldn't do anything here -- the most lagging thread is responsible for
      //       committing finalized records, to avoid useless concurrency on updating 'committed'
      //       cursor.
      //       But currently this logic breaks up on a end-of-page-padding record there currentRecord
      //       points to the actual record on the next page, while nextRecordToBeCommitted keeps pointing
      //       to padding record left on previous page.
      //       I work it around by trying to commit records always.
      //       Review it later, find way to avoid spending CPU
      tryCommitFinalizedRecords();

      return;
    }
  }

  private void tryCommitFinalizedRecords() throws IOException {
    try {
      while (true) {
        long firstYetUncommittedRecord = nextRecordToBeCommittedOffset.get();
        long allocatedUpTo = nextRecordToBeAllocatedOffset.get();
        if (firstYetUncommittedRecord == allocatedUpTo) {
          return; //nothing more to commit (yet)
        }
        Page page = storage.pageByOffset(firstYetUncommittedRecord);
        int offsetInPage = storage.toOffsetInPage(firstYetUncommittedRecord);
        int totalRecordLength = RecordLayout.recordLength(page.rawPageBuffer(), offsetInPage);
        if (totalRecordLength == 0) {
          return; //firstYetUncommittedRecord is not finalized (yet)
        }
        long nextUncommittedRecord = nextRecordOffset(firstYetUncommittedRecord, totalRecordLength);
        nextRecordToBeCommittedOffset.compareAndSet(firstYetUncommittedRecord, nextUncommittedRecord);
      }
    }
    finally {
      //MAYBE RC: probably an overkill -- better delay flush?
      flush();
    }
  }

  private long nextRecordOffset(long recordOffsetInFile,
                                int totalRecordLength) {
    long nextRecordOffset = recordOffsetInFile + totalRecordLength;

    int pageSize = storage.pageSize();
    int offsetInPage = storage.toOffsetInPage(nextRecordOffset);
    int remainingOnPage = pageSize - offsetInPage;
    if (remainingOnPage > RecordLayout.RECORD_HEADER_SIZE) {
      return nextRecordOffset;
    }
    //no room on the current page even for the record header => jump to the next page:
    return nextPageStartingOffset(nextRecordOffset, pageSize);
  }

  private static long nextPageStartingOffset(long recordOffsetInFile, int pageSize) {
    return ((recordOffsetInFile / pageSize) + 1) * pageSize;
  }


  private long recordOffsetToId(long recordOffset) {
    //0 is considered invalid id (NULL_ID) everywhere in our code, so '+1' for first id to be 1
    return recordOffset - HeaderLayout.HEADER_SIZE + 1;
  }

  private long recordIdToOffset(long recordId) {
    return recordId - 1 + HeaderLayout.HEADER_SIZE;
  }


  private int getIntHeaderField(int headerRelativeOffsetBytes) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (int)INT32_OVER_BYTE_BUFFER.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  private long getLongHeaderField(int headerRelativeOffsetBytes) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      return (long)INT64_OVER_BYTE_BUFFER.getVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes);
    }
  }

  private void setIntHeaderField(int headerRelativeOffsetBytes,
                                 int headerFieldValue) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      INT32_OVER_BYTE_BUFFER.setVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
    }
  }

  private void setLongHeaderField(int headerRelativeOffsetBytes,
                                  long headerFieldValue) throws IOException {
    try (Page page = storage.pageByOffset(headerRelativeOffsetBytes)) {
      INT64_OVER_BYTE_BUFFER.setVolatile(page.rawPageBuffer(), headerRelativeOffsetBytes, headerFieldValue);
    }
  }
}
