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

  public static final int MAX_PAYLOAD_SIZE = RecordLayout.RECORD_LENGTH_MASK;

  //TODO/MAYBE:
  //    1) connectionStatus: do we need it? Updates are atomic, every saved state is at least self-consistent.
  //       We could use (committed < allocated) as a marker of 'not everything was committed'
  //    2) Flush cursors values to header fields more often: it is crucial for the 'recoverability' -- only the section
  //       of the log until NEXT_RECORD_TO_BE_COMMITTED_OFFSET is recoverable after a crash.
  //       Maybe get rid of atomic fields, and update cursors plainly in a header?
  //    3) Cleanup-recovery after crash: if (committed < allocated) on start -- we need to set [allocated=committed],
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
    // Record = (header) + (payload)
    // Header = 32 bit, 32-bit-aligned (so it could be read/write as volatile, and not all CPU arch allow memory sync
    //          ops on non-aligned offsets)
    //          2 highest bits are reserved for record type and commitment status
    //          30 remaining bit: total record length, header included (i.e. payloadLength=recordLength-4)
    //          bit[31] (highest): record type, 0=regular record, 1=padding record
    //          bit[30]: record committed status, 0=record payload is fully written, 1=record payload is not yet
    //                   fully written

    /** 0==regular record, 1==padding record */
    private static final int RECORD_TYPE_MASK = 1 << 31;
    private static final int RECORD_TYPE_DATA = 0;
    private static final int RECORD_TYPE_PADDING = 1 << 31;

    /** 0==commited, 1==not commited */
    private static final int COMMITED_STATUS_MASK = 1 << 30;
    private static final int COMMITED_STATUS_OK = 1 << 30;
    private static final int COMMITED_STATUS_NOT_YET = 0;

    private static final int RECORD_LENGTH_MASK = 0x3FFF_FFFF;


    private static final int HEADER_OFFSET = 0;
    private static final int DATA_OFFSET = HEADER_OFFSET + Integer.BYTES;

    private static final int RECORD_HEADER_SIZE = Integer.BYTES;

    public static boolean isRecordFit(int offsetInPage,
                                      int pageSize) {
      return (pageSize - offsetInPage) >= RECORD_HEADER_SIZE;
    }

    public static void putDataRecord(@NotNull ByteBuffer buffer,
                                     int offsetInBuffer,
                                     byte[] data) {
      INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(data.length, /*commited: */false));
      buffer.put(offsetInBuffer + DATA_OFFSET, data);
      INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(data.length, /*commited: */true));
    }

    public static void putDataRecord(@NotNull ByteBuffer buffer,
                                     int offsetInBuffer,
                                     int payloadSize,
                                     ByteBufferWriter writer) throws IOException {
      INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(payloadSize, /*commited: */false));
      writer.write(buffer.slice(offsetInBuffer + DATA_OFFSET, payloadSize));
      INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + HEADER_OFFSET, dataRecordHeader(payloadSize, /*commited: */true));
    }

    public static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                        int offsetInBuffer) {
      int remainsToPad = buffer.capacity() - offsetInBuffer;
      putPaddingRecord(buffer, offsetInBuffer, remainsToPad);
    }

    private static void putPaddingRecord(@NotNull ByteBuffer buffer,
                                         int offsetInBuffer,
                                         int remainsToPad) {
      if (remainsToPad < RECORD_HEADER_SIZE) {
        throw new IllegalArgumentException(
          "Can't create PaddingRecord for " + remainsToPad + "b leftover, must be >" + RECORD_HEADER_SIZE + " b left:" +
          "buffer.capacity(=" + buffer.capacity() + "), offsetInBuffer(=" + offsetInBuffer + ")");
      }
      int header = paddingRecordHeader(remainsToPad, /*commited: */true);
      INT32_OVER_BYTE_BUFFER.setRelease(buffer, offsetInBuffer + HEADER_OFFSET, header);
    }


    /** generates data record header given length of payload and commited status */
    private static int dataRecordHeader(int payloadLength,
                                        boolean commited) {
      int totalRecordSize = payloadLength + RECORD_HEADER_SIZE;
      if ((totalRecordSize & COMMITED_STATUS_MASK) != 0
          || (totalRecordSize & RECORD_TYPE_MASK) != 0) {
        throw new IllegalArgumentException("totalRecordSize(=" + totalRecordSize + ") must have 2 highest bits 0");
      }

      return totalRecordSize | (commited ? COMMITED_STATUS_OK : COMMITED_STATUS_NOT_YET);
    }

    /** generates padding record header given total length of padding, and commited status */
    private static int paddingRecordHeader(int lengthToPad,
                                           boolean commited) {
      if (lengthToPad == 0) {
        throw new IllegalArgumentException("lengthToPad(=" + lengthToPad + ") must be >0");
      }
      int totalRecordSize = lengthToPad;
      if ((totalRecordSize & (~RECORD_LENGTH_MASK)) != 0) {
        throw new IllegalArgumentException("lengthToPad(=" + lengthToPad + ") must have 2 highest bits 0");
      }

      return totalRecordSize | RECORD_TYPE_MASK | (commited ? 0 : COMMITED_STATUS_MASK);
    }

    /** @return total record length (including header) */
    private static int readRecordLength(ByteBuffer buffer,
                                        int offsetInBuffer) {
      int header = readHeader(buffer, offsetInBuffer);
      return extractRecordLength(header);
    }

    /** @return total record length (including header) */
    private static int extractRecordLength(int header) {
      return roundUpToInt32(header & RECORD_LENGTH_MASK);
    }

    /** @return record payload length (excluding header) */
    private static int extractPayloadLength(int header) {
      return (header & RECORD_LENGTH_MASK) - RECORD_HEADER_SIZE;
    }

    private static boolean isPaddingHeader(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_PADDING;
    }

    private static boolean isDataHeader(int header) {
      return (header & RECORD_TYPE_MASK) == RECORD_TYPE_DATA;
    }

    private static boolean isRecordCommitted(int header) {
      return (header & COMMITED_STATUS_MASK) == COMMITED_STATUS_OK;
    }

    public static int readHeader(ByteBuffer buffer,
                                 int offsetInBuffer) {
      return (int)INT32_OVER_BYTE_BUFFER.getVolatile(buffer, offsetInBuffer + HEADER_OFFSET);
    }

    private static int calculateRecordLength(int payloadSize) {
      return roundUpToInt32(payloadSize + RECORD_HEADER_SIZE);
    }

    public static ByteBuffer recordDataAsSlice(ByteBuffer buffer,
                                               int offsetInBuffer) {
      int header = readHeader(buffer, offsetInBuffer);

      if (!isDataHeader(header)) {
        throw new IllegalStateException("record[" + offsetInBuffer + "] is " +
                                        "PaddingRecord(header=" + header + ") -- i.e. has no data");
      }
      else if (!isRecordCommitted(header)) {
        throw new IllegalStateException("record[" + offsetInBuffer + "] is not commited (header=" + header + ") " +
                                        "either not yet written or corrupted");
      }
      int payloadLength = extractPayloadLength(header);
      return buffer.slice(offsetInBuffer + DATA_OFFSET, payloadLength);
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

    int pageSize = storage.pageSize();
    if (!is32bAligned(pageSize)) {
      throw new IllegalArgumentException("storage.pageSize(=" + pageSize + ") must be 32b-aligned");
    }

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

    long nextRecordToBeCommittedOffset = getLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET);
    if (nextRecordToBeCommittedOffset == UNSET_VALUE) {//log is just created:
      nextRecordToBeCommittedOffset = HeaderLayout.HEADER_SIZE;
      setLongHeaderField(HeaderLayout.NEXT_RECORD_TO_BE_COMMITTED_OFFSET, nextRecordToBeCommittedOffset);
    }

    if (nextRecordToBeCommittedOffset < nextRecordToBeAllocatedOffset) {
      //storage wasn't closed correctly -- probably forced restart? -> need to recover

      //For recovery we need 2 things:
      // - convert all non-commited records to padding
      // - clear everything after nextRecordToBeAllocatedOffset (everything after nextRecordToBeAllocatedOffset must be 0)

      long successfullyRecoveredUntil = recoverRegion(nextRecordToBeCommittedOffset, nextRecordToBeAllocatedOffset);

      //MAYBE RC: really we should take actual file size, and zero it from nextRecordToBeCommittedOffset and till
      //          the end
      Page page = storage.pageByOffset(successfullyRecoveredUntil);
      int offsetInPage = storage.toOffsetInPage(successfullyRecoveredUntil);
      page.rawPageBuffer().put(new byte[pageSize-offsetInPage]);

      nextRecordToBeCommittedOffset = successfullyRecoveredUntil;
      nextRecordToBeAllocatedOffset = successfullyRecoveredUntil;

    }


    this.nextRecordToBeAllocatedOffset.set(nextRecordToBeAllocatedOffset);
    this.nextRecordToBeCommittedOffset.set(nextRecordToBeCommittedOffset);
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
                     int payloadSize) throws IOException {
    if (payloadSize < 0) {
      throw new IllegalArgumentException("Can't write record with payloadSize(=" + payloadSize + ") < 0");
    }
    int pageSize = storage.pageSize();
    if (payloadSize > pageSize - RecordLayout.RECORD_HEADER_SIZE) {
      throw new IllegalArgumentException("payloadSize(=" + payloadSize + ") is too big: " +
                                         "record with header must fit pageSize(=" + pageSize + ")");
    }

    int totalRecordLength = RecordLayout.calculateRecordLength(payloadSize);
    long recordOffsetInFile = allocateSpaceForRecord(totalRecordLength);
    check32bAligned(recordOffsetInFile, "recordOffsetInFile");

    Page page = storage.pageByOffset(recordOffsetInFile);
    int offsetInPage = storage.toOffsetInPage(recordOffsetInFile);

    RecordLayout.putDataRecord(page.rawPageBuffer(), offsetInPage, payloadSize, writer);

    tryCommitRecord(recordOffsetInFile, totalRecordLength);

    return recordOffsetToId(recordOffsetInFile);
  }

  //MAYBE RC: Implementation below is a bit faster than default version (no lambda, no buffer slice).
  //          But does it worth it?
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
    long firstUnallocatedOffset = nextRecordToBeAllocatedOffset.get();
    for (long offsetInFile = HeaderLayout.HEADER_SIZE;
         offsetInFile < firstUnallocatedOffset; ) {
      Page page = storage.pageByOffset(offsetInFile);
      int recordOffsetInPage = storage.toOffsetInPage(offsetInFile);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - recordOffsetInPage <= RecordLayout.RECORD_HEADER_SIZE) {
        throw new IOException(
          "Storage corrupted: recordOffsetInPage(=" + recordOffsetInPage + ") less than " +
          "RECORD_HEADER(=" + RecordLayout.RECORD_HEADER_SIZE + "b) left until " +
          "pageEnd(" + pageSize + ") -- all records must be 32b-aligned"
        );
      }

      int recordHeader = RecordLayout.readHeader(pageBuffer, recordOffsetInPage);
      int recordLength = RecordLayout.extractRecordLength(recordHeader);
      if (recordHeader == 0) {
        //the record wasn't even started to be written -> stop
        return true;
      }

      if (RecordLayout.isDataHeader(recordHeader)) {
        if (RecordLayout.isRecordCommitted(recordHeader)) {
          ByteBuffer recordDataSlice = RecordLayout.recordDataAsSlice(pageBuffer, recordOffsetInPage);
          long recordId = recordOffsetToId(offsetInFile);
          boolean shouldContinue = reader.read(recordId, recordDataSlice);
          if (!shouldContinue) {
            return false;
          }
        }//else: record not yet commited, so skip it -- but _maybe_ next record(s) are committed?
      }
      else if (RecordLayout.isPaddingHeader(recordHeader)) {
        //just skip it
      }
      else {
        //if header != 0 => it must be either padding, or (uncommitted?) data record:
        throw new IOException("header(=" + recordHeader + "](@offset=" + offsetInFile + "): not a padding, nor a data record");
      }


      offsetInFile = nextRecordOffset(offsetInFile, recordLength);
    }

    //TODO: there could be some finalized records in [committed, allocated) -- is it better to process them also?
    return true;
  }

  private long recoverRegion(long nextRecordToBeCommittedOffset,
                             long nextRecordToBeAllocatedOffset) throws IOException {
    int pageSize = storage.pageSize();
    for (long offsetInFile = nextRecordToBeCommittedOffset;
         offsetInFile < nextRecordToBeAllocatedOffset; ) {
      Page page = storage.pageByOffset(offsetInFile);
      int recordOffsetInPage = storage.toOffsetInPage(offsetInFile);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      if (pageSize - recordOffsetInPage <= RecordLayout.RECORD_HEADER_SIZE) {
        throw new IOException(
          "Storage corrupted: recordOffsetInPage(=" + recordOffsetInPage + ") less than " +
          "RECORD_HEADER(=" + RecordLayout.RECORD_HEADER_SIZE + "b) left until " +
          "pageEnd(" + pageSize + ") -- all records must be 32b-aligned"
        );
      }

      int recordHeader = RecordLayout.readHeader(pageBuffer, recordOffsetInPage);
      int recordLength = RecordLayout.extractRecordLength(recordHeader);

      if (recordLength == 0) {
        //Can't recover farther: actual length of record is unknown length
        return offsetInFile;
      }
      if (RecordLayout.isDataHeader(recordHeader)) {
        if (!RecordLayout.isRecordCommitted(recordHeader)) {
          //Unfinished record: convert it to padding record
          RecordLayout.putPaddingRecord(pageBuffer, recordOffsetInPage, recordLength);
        }//else: record OK -> move to the next one
      }
      else if (RecordLayout.isPaddingHeader(recordHeader)) {
        //padding is always committed -> move to the next one
      }
      else {
        //Unrecognizable garbage: we could just stop recovering here, and erase everything from
        //  here and up -- but for now I'd prefer to know how that could even happen (could it?)
        throw new IOException("header(=" + recordHeader + "](@offset=" + offsetInFile + "): not a padding, nor a data record");
      }

      offsetInFile = nextRecordOffset(offsetInFile, recordLength);
    }
    return nextRecordToBeAllocatedOffset;
  }

  public void clear() throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  public void flush() throws IOException {
    flush(false);
  }

  @Override
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
    if (totalRecordLength > pageSize) {
      throw new IllegalArgumentException("totalRecordLength(=" + totalRecordLength + ") must fit the page(=" + pageSize + ")");
    }
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
      if (remainingOnPage >= RecordLayout.RECORD_HEADER_SIZE) {
        if (nextRecordToBeAllocatedOffset.compareAndSet(recordOffsetInFile, recordOffsetInFile + remainingOnPage)) {
          Page page = storage.pageByOffset(recordOffsetInFile);
          RecordLayout.putPaddingRecord(page.rawPageBuffer(), recordOffsetInPage);
        }//else: somebody else dealt with that offset -> retry anyway
        continue;
      }

      //With record offsets AND lengths AND pageSize all 32b-aligned -- it could be either 0 or 4 bytes at the end
      // of the page -- all those options processed above -- but never 1-2-3 bytes.
      throw new AssertionError("Bug: remainingOnPage(=" + remainingOnPage + ") < RECORD_HEADER(=" + RecordLayout.RECORD_HEADER_SIZE + ")," +
                               "but records must be 32b-aligned, so it must never happen. " +
                               "recordOffsetInFile(=" + recordOffsetInFile + "), " +
                               "recordOffsetInPage(=" + recordOffsetInPage + "), " +
                               "totalRecordLength(=" + totalRecordLength + ")");
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
        int totalRecordLength = RecordLayout.readRecordLength(page.rawPageBuffer(), offsetInPage);
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

  /**
   * @return offset of the next record, given current record starting (=header) offset, and the record length.
   * Takes into account record alignment to word/pages boundaries, etc.
   */
  private long nextRecordOffset(long recordOffsetInFile,
                                int totalRecordLength) {
    check32bAligned(recordOffsetInFile, "recordOffsetInFile");
    long nextRecordOffset = roundUpToInt32(recordOffsetInFile + totalRecordLength);

    int pageSize = storage.pageSize();
    int offsetInPage = storage.toOffsetInPage(nextRecordOffset);
    int remainingOnPage = pageSize - offsetInPage;
    if (remainingOnPage == RecordLayout.RECORD_HEADER_SIZE) {
      //no room on the current page even for the record header => jump to the next page:
      return nextPageStartingOffset(recordOffsetInFile, pageSize);
    }
    else if (remainingOnPage < RecordLayout.RECORD_HEADER_SIZE) {
      throw new IllegalStateException(
        "remainingOnPage(=" + remainingOnPage + ") <= recordHeader(=" + RecordLayout.RECORD_HEADER_SIZE + ")");
    }
    return nextRecordOffset;
  }

  /** @return starting offset of the next page, given recordOffsetInFile in current page, and pageSize */
  private static long nextPageStartingOffset(long recordOffsetInFile,
                                             int pageSize) {
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

  //================== alignment: ========================================================================
  // Record headers must be 32b-aligned so they could be accessed with volatile semantics -- because not
  // all CPU arch allow unaligned access with memory sync semantics

  private static int roundUpToInt32(int value) {
    if (is32bAligned(value)) {
      return value;
    }
    return ((value >> 2) + 1) << 2;
  }

  private static long roundUpToInt32(long value) {
    if (is32bAligned(value)) {
      return value;
    }
    return ((value >> 2) + 1) << 2;
  }

  private static boolean is32bAligned(int value) {
    return (value & 0b11) == 0;
  }

  private static boolean is32bAligned(long value) {
    return (value & 0b11L) == 0;
  }

  private static void check32bAligned(long value,
                                      @NotNull String name) {
    if (!is32bAligned(value)) {
      throw new AssertionError("Bug: " + name + "(=" + value + ") is not 32b-aligned");
    }
  }
}
