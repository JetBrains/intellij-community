// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStreamBase;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.io.*;
import com.intellij.util.io.blobstorage.ByteBufferReader;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.RecordAlreadyDeletedException;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.checkAttributeValueSize;
import static com.intellij.openapi.vfs.newvfs.persistent.FSRecords.LOG;
import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Attribute storage implemented on the top of {@link StreamlinedBlobStorage}
 */
@ApiStatus.Internal
public final class AttributesStorageOverBlobStorage implements AbstractAttributesStorage, CleanableStorage {
  public static final int MAX_SUPPORTED_ATTRIBUTE_ID = 1 << AttributeEntry.BIG_ENTRY_ATTR_ID_BITS;

  /**
   * Under correct usage, we should never delete already deleted attribute record -- which is why storage was made
   * throw exception in such a case.
   * But in case of unexpected app termination, delete op could be interrupted in the middle, hence continues
   * on restart, leading to the AlreadyDeletedException. It may be worth ignoring such errors, i.e. effectively
   * allowing to delete already deleted record one more time. This is risky -- storage is definitely compromised
   * -- but it could 'self-heal' storage in case of small corruptions without bothering the user with full VFS
   * rebuild (which is a default way to 'self-heal' compromised VFS).
   */
  public static final boolean IGNORE_ALREADY_DELETED_ERRORS = getBooleanProperty("vfs.attributes.ignore-already-deleted-errors", true);

  //Persistent format (see AttributesRecord/AttributeEntry):
  //  Storage := (AttributeDirectoryRecord | AttributeDedicatedRecord)*
  //
  //  AttributeDirectoryRecord: header, entry*
  //      header:  fileId[4b]   (ref back to file owned attributes)
  //      entry:  attributeId[?], inlineSizeOrRefId[?], inlineData[inlineSizeOrRefId]?
  //              (elaborated schema to encode attributeId+size into 1 or 2 bytes, or 6 bytes)
  //
  //      Attribute values <= INLINE_ATTRIBUTE_MAX_SIZE stored inline, size in .inlineSizeOrRefId,
  //      Attribute values >  INLINE_ATTRIBUTE_MAX_SIZE stored in dedicated records, with
  //      dedicatedRecordId = (inlineSizeOrRefId-INLINE_ATTRIBUTE_MAX_SIZE), inlineData is absent
  //      for them.
  //
  //  AttributeDedicatedRecord: header, data[...]
  //      header: -fileId[4b], attributeId[4b]
  //               (fileId refs back to file owned attribute, '-' distinguishes dedicated record
  //               from directory record)
  //      data[...]: attribute value, size = record.length-header.size(8)

  private final @NotNull StreamlinedBlobStorage storage;

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public AttributesStorageOverBlobStorage(final @NotNull StreamlinedBlobStorage storage) { this.storage = storage; }

  @Override
  public int getVersion() throws IOException {
    return storage.getDataFormatVersion();
  }

  @Override
  public void setVersion(final int version) throws IOException {
    storage.setDataFormatVersion(version);
  }

  @Override
  public @Nullable AttributeInputStream readAttribute(final @NotNull PersistentFSConnection connection,
                                                      final int fileId,
                                                      final @NotNull FileAttribute attribute) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.readLock().lock();
    try {
      final int attributeRecordId = connection.getRecords().getAttributeRecordId(fileId);
      if (attributeRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return null;
      }
      else if (attributeRecordId < NON_EXISTENT_ATTR_RECORD_ID) {
        throw new IllegalStateException("file[id: " + fileId + "]: attributeRecordId[=" + attributeRecordId + "] is negative, must be >=0");
      }
      final int encodedAttributeId = connection.getAttributeId(attribute.getId());

      final byte[] attributeValueBytes = readAttributeValue(
        attributeRecordId,
        fileId, encodedAttributeId
      );

      if (attributeValueBytes == null) {
        return null;
      }
      return new AttributeInputStream(
        new UnsyncByteArrayInputStream(attributeValueBytes),
        connection.getEnumeratedAttributes()
      );
    }
    finally {
      lock.readLock().unlock();
    }
  }

  public <R> R readAttributeRaw(final PersistentFSConnection connection,
                                final int fileId,
                                final @NotNull FileAttribute attribute,
                                final ByteBufferReader<R> reader) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.readLock().lock();
    try {
      final int attributeRecordId = connection.getRecords().getAttributeRecordId(fileId);
      if (attributeRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return null;
      }
      if (attributeRecordId < 0) {
        throw new IllegalStateException("file[id: " + fileId + "]: attributeRecordId[=" + attributeRecordId + "] is negative, must be >=0");
      }
      final int encodedAttributeId = connection.getAttributeId(attribute.getId());

      return readAttributeValue(
        attributeRecordId,
        fileId,
        encodedAttributeId,
        reader
      );
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public boolean hasAttributePage(final @NotNull PersistentFSConnection connection,
                                  final int fileId,
                                  final @NotNull FileAttribute attribute) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.readLock().lock();
    try {
      final int attributeRecordId = connection.getRecords().getAttributeRecordId(fileId);
      if (attributeRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return false;
      }
      final int encodedAttributeId = connection.getAttributeId(attribute.getId());

      return hasAttribute(attributeRecordId, fileId, encodedAttributeId);
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public @NotNull AttributeOutputStream writeAttribute(final @NotNull PersistentFSConnection connection,
                                                       final int fileId,
                                                       final @NotNull FileAttribute attribute) {
    return new AttributeOutputStreamBase(
      new AttributeOutputStreamImpl(connection, fileId, attribute),
      connection.getEnumeratedAttributes()
    );
  }

  @Override
  public void deleteAttributes(final @NotNull PersistentFSConnection connection,
                               final int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);

    PersistentFSRecordsStorage records = connection.getRecords();
    lock.writeLock().lock();
    try {
      final int attributeRecordId = records.getAttributeRecordId(fileId);
      deleteAttributes(attributeRecordId, fileId);

      records.setAttributeRecordId(fileId, NON_EXISTENT_ATTR_RECORD_ID);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void checkAttributeRecordSanity(int fileId,
                                         int attributeRecordId) throws IOException {
    if (attributeRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
      return;
    }

    lock.readLock().lock();
    try {
      storage.readRecord(attributeRecordId, buffer -> {
        AttributesRecord attributesRecord = new AttributesRecord(buffer);
        if (!attributesRecord.isValid()) {
          throw new IllegalStateException(
            "record(" + attributeRecordId + ") is invalid: " + attributesRecord
          );
        }
        attributesRecord.checkBackrefFile(attributeRecordId, fileId);

        if (attributesRecord.hasDirectory()) {
          int entryNo = 0;
          for (AttributeEntry entry = attributesRecord.currentEntry();
               entry.isValid();
               entry.moveToNextEntry(), entryNo++) {
            int attributeId = entry.attributeId();
            if (entry.isValueInlined()) {
              if (attributeId <= DataEnumerator.NULL_ID || attributeId > MAX_SUPPORTED_ATTRIBUTE_ID) {
                String valueAsHex = IOUtil.toHexString(entry.inlinedValueAsSlice());
                throw new IllegalStateException(
                  "attributeRecord[id:" + attributeRecordId + "][#" + entryNo + "]" +
                  "{attributeId: " + attributeId + ", value: " + valueAsHex + "} attributeId must be in [1.." + MAX_ATTRIBUTE_ID + "]");
              }
            }
            else {
              int dedicatedRecordId = entry.dedicatedValueRecordId();
              if (!storage.hasRecord(dedicatedRecordId)) {
                throw new IllegalStateException(
                  "attributeRecord[id:" + attributeRecordId + "][#" + entryNo + "]" +
                  "{attributeId: " + attributeId + ", dedicatedId: " + dedicatedRecordId + "} dedicatedId must be != 0");
              }
            }
          }
        }
        else if (attributesRecord.hasDedicatedAttribute()) {
          int attributeId = attributesRecord.dedicatedRecordAttributeId();
          throw new IllegalStateException(
            "attributeRecord[id:" + attributeRecordId + "]{attributeId: " + attributeId + "} " +
            "is dedicated record, but must be a directory record" +
            ": " + attributesRecord
          );
        }
        else {//AssertionError because it must be covered by !isValid() branch above
          throw new AssertionError(
            "attributeRecord[id:" + attributeRecordId + "] " +
            "is of unknown type (!directory & !dedicated) record" +
            ": " + attributesRecord
          );
        }
        return null;
      });
    }
    finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public boolean isEmpty() {
    return storage.liveRecordsCount() == 0;
  }

  /**
   * BEWARE: recordId reported to callback is not always the same recordId as was inserted: during insert/update recordId of
   * 'directory' record is returned, but attributes could be actually stored in additional 'dedicated' records (if they are
   * not fit for storing inline in 'directory' record). This dedicated record id will be reported to callback for such
   * 'big' attributes (see details in the method body)
   */
  public <E extends Exception> void forEachAttribute(final Processor<E> processor) throws IOException, E {
    // RC: For dedicated records ID of dedicated record reported, instead of id of apt. parent 'directory' record -- simply
    // because this is there attribute value is found during file scan-through -- while at insert phase it would be _directory
    // record_ id.
    // But such a behaviour, surely, a surprise from a client PoV.
    // And surely, this could be fixed in the implementation, but with the cost: basically, we'll need to not report dedicated
    // records to the callback immediately, but keep track of them, and report them only after their 'parent' directory records
    // are met, hence 'true' recordId could be determined.
    // This is surely doable, but for now it seems an overkill to do that -- for the practical use cases of .forEachAttribute I
    // have in my mind now fileId and attributeId are important, but 'true' recordId is really not important. And the purpose of
    // this method is to provide fast _streaming-like_ access to storage.
    // Hence, I decided to delay more correct implementation until the need for it satisfies its cost.
    lock.readLock().lock();
    try {
      storage.forEach((recordId, recordCapacity, recordLength, payload) -> {
        if (!storage.isRecordActual(recordLength)) {
          return true;
        }

        final AttributesRecord attributesRecord = new AttributesRecord(payload);
        if (attributesRecord.hasDirectory()) {
          final int fileId = attributesRecord.fileId();
          for (final AttributeEntry attributeEntry = attributesRecord.currentEntry();
               attributeEntry.isValid();
               attributeEntry.moveToNextEntry()) {
            final int attributeId = attributeEntry.attributeId();
            if (attributeEntry.isValueInlined()) {
              final byte[] valueBytes = attributeEntry.inlinedValueAsByteArray();
              processor.processAttribute(recordId, fileId, attributeId, valueBytes, /*inlined: */ true);
            }
          }
        }
        else if (attributesRecord.hasDedicatedAttribute()) {
          final int fileId = attributesRecord.fileId();
          final int attributeId = attributesRecord.dedicatedRecordAttributeId();
          final byte[] valueBytes = attributesRecord.dedicatedValueAsByteArray();
          processor.processAttribute(recordId, fileId, attributeId, valueBytes, /*inlined: */ false);
        }
        return true;
      });
    }
    finally {
      lock.readLock().unlock();
    }
  }

  public interface Processor<E extends Exception> {
    void processAttribute(final int recordId,
                          final int fileId,
                          final int attributeId,
                          final byte[] attributeValue,
                          final boolean inlinedAttribute) throws E;
  }

  @Override
  public boolean isDirty() {
    return storage.isDirty();
  }

  @Override
  public void force() throws IOException {
    storage.force();
  }

  @Override
  public void close() throws IOException {
    storage.close();
  }

  @Override
  public void closeAndClean() throws IOException {
    storage.closeAndClean();
  }

  /**
   * 'get' methods all use absolute buffer access methods, hence don't move buffer position/limit,
   * but 'put' methods use relative buffer access, and move buffer position (because this is that expected
   * by calling code)
   */
  @VisibleForTesting
  protected static final class AttributesRecord {

    public static final int RECORD_FILE_ID_OFFSET = 0;
    public static final int RECORD_HEADER_SIZE = Integer.BYTES;

    public static final int DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET = RECORD_FILE_ID_OFFSET + Integer.BYTES;
    public static final int DEDICATED_RECORD_HEADER_SIZE = DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET + Integer.BYTES;

    /** Record buffer */
    private final ByteBuffer buffer;
    /** Full record length (i.e. header + all attributes entries) */
    private final int length;

    private final int backRefFileId;
    private final int dedicatedAttributeId;


    private final AttributeEntry entry = new AttributeEntry();

    public AttributesRecord(final ByteBuffer buffer) throws IOException {
      this.buffer = buffer;
      this.length = buffer.remaining();

      if (length >= RECORD_HEADER_SIZE) {
        final int fileId = buffer.getInt(RECORD_FILE_ID_OFFSET);
        if (fileId < 0) {//this is a dedicated attribute record, not a directory record:
          if (length < DEDICATED_RECORD_HEADER_SIZE) {
            throw new CorruptedException("record length(=" + length + ") must be > " + DEDICATED_RECORD_HEADER_SIZE + ", " +
                                         "buffer: " + IOUtil.toHexString(buffer));
          }
          backRefFileId = -fileId;
          dedicatedAttributeId = buffer.getInt(DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET);
          //no entries in a dedicated record => no need for entry.reset(...)
        }
        else {//this is a directory record:
          backRefFileId = fileId;
          dedicatedAttributeId = -1;
          entry.reset(RECORD_HEADER_SIZE, buffer);
        }
      }
      else { //record is not exist (yet?): for creating new record from 0
        backRefFileId = -1;
        dedicatedAttributeId = -1;
      }
    }

    /**
     * @return true if the record is one of valid types: either directory or dedicated.
     * false otherwise: if it is empty (length=0), or some kind of broken/corrupted record
     */
    public boolean isValid() {
      return hasDirectory() || hasDedicatedAttribute();
    }

    public void checkBackrefFile(int attributesRecordId, int fileId) throws CorruptedException {
      if (backRefFileId != fileId) {
        throw new CorruptedException("record(" + attributesRecordId + "): " +
                                     "fileId(" + fileId + ") != backref fileId(" + backRefFileId + "), " +
                                     this
        );
      }
    }

    public int fileId() {
      return backRefFileId;
    }

    public boolean findAttributeInDirectoryRecord(final int lookupAttributeId) {
      for (entry.reset(RECORD_HEADER_SIZE, buffer);
           entry.isValid();
           entry.moveToNextEntry()) {
        final int attributeId = entry.attributeId();
        if (lookupAttributeId == attributeId) {
          return true;
        }
      }
      return false;
    }

    public AttributeEntry currentEntry() {
      return entry;
    }

    public boolean hasDirectory() {
      return length >= RECORD_HEADER_SIZE
             && dedicatedAttributeId < 0;
    }

    public boolean hasDedicatedAttribute() {
      return length >= DEDICATED_RECORD_HEADER_SIZE
             && dedicatedAttributeId > 0;
    }

    /**
     * Valid for dedicated attribute record, -1 otherwise
     */
    private int dedicatedRecordAttributeId() {
      return dedicatedAttributeId;
    }

    public byte[] dedicatedValueAsByteArray() {
      final byte[] recordValue = new byte[length - DEDICATED_RECORD_HEADER_SIZE];
      buffer.get(DEDICATED_RECORD_HEADER_SIZE, recordValue);
      return recordValue;
    }

    @Override
    public String toString() {
      return "AttributesRecord[" + (hasDirectory() ? "directory" : "dedicated") + "][" +
             buffer.position() + ".." + buffer.limit() + ", length: " + length + "]" +
             "[backRefFileId: " + backRefFileId + ", dedicatedAttributeId: " + dedicatedAttributeId + "]";
    }

    public static ByteBuffer putDirectoryRecordHeader(final ByteBuffer buffer,
                                                      final int fileId) {
      return buffer.putInt(fileId);
    }

    public static ByteBuffer putDedicatedValueRecordHeader(final ByteBuffer buffer,
                                                           final int fileId,
                                                           final int attributeId) {
      return buffer
        .putInt(-fileId) //negative fileId backref is a sign of dedicated attribute record
        .putInt(attributeId);
    }

    public static int dedicatedRecordSizeForValueSize(final int valueSize) {
      return DEDICATED_RECORD_HEADER_SIZE + valueSize;
    }
  }

  @VisibleForTesting
  protected static final class AttributeEntry {
    //Entry binary format:
    //    We try hard to be as compact as possible. This is because we have really a lot of very small attributes:
    //    2-10 bytes attributes are very common (and they are the most frequently queried/updated), and >97% of
    //    all attributes are <64 bytes. Hence, it is quite important to store few-bytes attributes with header as
    //    compact as possible: even 2 bytes header is 100% overhead for 2 bytes attribute.
    //    To implement that, we use 3 'sizes' of attribute record: tiny, medium and big. Tiny record is the smallest
    //    one, it combines both attributeId and size into 1 byte -- i.e. it works for attributeId < 16 && size < 8,
    //    which covers a significant part of very small attributes.
    //    Entry sizes differentiated by first bits of 1st header byte:
    //    1. if first bit  = 0...  -> tiny record (7 remaining bits are attributeId+size)
    //    2. if first bits = 10... -> medium record (6 bits remaining + 8 bits of next byte are attributeId+size)
    //    3. if first bits = 11... -> big record (6 bits remaining + next byte is attributeId, next 4 bytes is size)

    private static final int SMALLEST_HEADER_SIZE = 1;
    private static final int MEDIUM_HEADER_SIZE = 2;
    private static final int BIG_HEADER_SIZE = 6;

    //Tiny entry: header=1 byte, 3 bits for attributeId, 4 bits for size
    private static final byte TINY_ENTRY_MASK = (byte)0b1000_0000;
    private static final int TINY_ENTRY_ATTR_ID_BITS = 3;
    private static final int TINY_ENTRY_ATTR_ID_MASK = 0b0111_0000;
    private static final int TINY_ENTRY_SIZE_MASK = 0b0000_1111;
    private static final int TINY_ENTRY_SIZE_BITS = 4;
    private static final int TINY_ENTRY_MAX_ATTRIBUTE_ID = 0b111;
    private static final int TINY_ENTRY_MAX_SIZE = 0b1111;

    //Medium entry: header=2 bytes, 6 bits for attributeId, 8 bits for size (to be discussed: 7+7 instead)
    private static final byte MEDIUM_ENTRY_MASK = (byte)0b0100_0000;
    private static final int MEDIUM_ENTRY_ATTR_ID_BITS = 6;
    private static final int MEDIUM_ENTRY_SIZE_BITS = 8;
    private static final int MEDIUM_ENTRY_MAX_ATTRIBUTE_ID = 0b11_1111;
    private static final int MEDIUM_ENTRY_MAX_SIZE = 0b1111_1111;

    //Big entry: header=6 bytes, (6+8=14) bits for attributeId, 32 bits for size/refId
    private static final byte BIG_ENTRY_MASK = (byte)0b1100_0000;
    private static final int BIG_ENTRY_ATTR_ID_OF_FIRST_BYTE_MASK = 0b0011_1111;
    private static final int BIG_ENTRY_ATTR_ID_BITS = 14;

    private int entryStartOffset;
    private int attributeId;
    private int inlinedValueSizeOrDedicatedRecordId;

    private int headerSize = -1;

    private ByteBuffer buffer;

    public void reset(final int entryStartOffset,
                      final ByteBuffer buffer) {
      this.entryStartOffset = entryStartOffset;
      this.buffer = buffer;

      if (isValid()) {
        final byte firstByte = buffer.get(entryStartOffset);
        if ((firstByte & TINY_ENTRY_MASK) == 0) {//1 byte for (attributeId,size):
          attributeId = ((firstByte & TINY_ENTRY_ATTR_ID_MASK) >> TINY_ENTRY_SIZE_BITS);
          inlinedValueSizeOrDedicatedRecordId = (firstByte & TINY_ENTRY_SIZE_MASK);
          headerSize = SMALLEST_HEADER_SIZE;
        }
        else if ((firstByte & MEDIUM_ENTRY_MASK) == 0) {//2 bytes for (attributeId,size):
          assertMediumHeaderIsValid(buffer, entryStartOffset, firstByte);
          final byte secondByte = buffer.get(entryStartOffset + 1);
          attributeId = firstByte & MEDIUM_ENTRY_MAX_ATTRIBUTE_ID;      //zero out first 2 bits
          inlinedValueSizeOrDedicatedRecordId = Byte.toUnsignedInt(secondByte);
          headerSize = MEDIUM_HEADER_SIZE;
        }
        else {//6 bytes for (attributeId, size):
          assertBigHeaderIsValid(buffer, entryStartOffset, firstByte);

          final byte secondByte = buffer.get(entryStartOffset + 1);
          attributeId = ((firstByte & BIG_ENTRY_ATTR_ID_OF_FIRST_BYTE_MASK) << Byte.SIZE) | Byte.toUnsignedInt(secondByte);
          inlinedValueSizeOrDedicatedRecordId = buffer.getInt(entryStartOffset + 2);
          headerSize = BIG_HEADER_SIZE;
        }
      }
      else {//entry does not exist here (branch for to-be-created entry)
        attributeId = -1;
        inlinedValueSizeOrDedicatedRecordId = -1;
        headerSize = -1;
      }
    }

    public boolean isValid() {
      return entryStartOffset + SMALLEST_HEADER_SIZE <= buffer.limit();
    }

    public int attributeId() {
      return attributeId;
    }

    public boolean isValueInlined() {
      return 0 <= inlinedValueSizeOrDedicatedRecordId && inlinedValueSizeOrDedicatedRecordId < INLINE_ATTRIBUTE_SMALLER_THAN;
    }

    public int dedicatedValueRecordId() {
      return inlinedValueSizeOrDedicatedRecordId - INLINE_ATTRIBUTE_SMALLER_THAN;
    }

    public int inlinedValueStartOffset() {
      return entryStartOffset + headerSize;
    }

    public int inlinedValueLength() {
      return isValueInlined() ? inlinedValueSizeOrDedicatedRecordId : 0;
    }

    public int offset() {
      return entryStartOffset;
    }

    public int nextEntryOffset() {
      return entryStartOffset + entrySize();
    }

    public int entrySize() {
      return headerSize
             + (isValueInlined() ? inlinedValueLength() : 0);
    }

    public boolean moveToNextEntry() {
      final int offset = nextEntryOffset();
      if (offset >= buffer.remaining()) {
        entryStartOffset = offset;
        //but don't read anything (as in .reset()) since this is invalid offset
        return false;
      }
      reset(offset, buffer);
      return true;
    }

    public byte[] inlinedValueAsByteArray() {
      final int valueLength = inlinedValueLength();
      final byte[] entryValue = new byte[valueLength];
      buffer.get(inlinedValueStartOffset(), entryValue);
      return entryValue;
    }

    public ByteBuffer inlinedValueAsSlice() {
      final int valueLength = inlinedValueLength();
      return buffer.slice(inlinedValueStartOffset(), valueLength)
        .order(buffer.order());
    }

    @Override
    public String toString() {
      return "AttributeEntry{" +
             "startOffset: " + entryStartOffset +
             ", attributeId: " + attributeId +
             ", valid: " + isValid() + "}{inlinedValueSizeOrDedicatedRecordId=" + inlinedValueSizeOrDedicatedRecordId +
             ", buffer=" + buffer +
             '}';
    }

    public static ByteBuffer putInlineEntryHeader(final ByteBuffer buffer,
                                                  final int encodedAttributeId,
                                                  final int newValueSize) {
      assert newValueSize < INLINE_ATTRIBUTE_SMALLER_THAN : newValueSize + " >= " + INLINE_ATTRIBUTE_SMALLER_THAN;
      return putEntryHeader(buffer, encodedAttributeId, newValueSize);
    }

    private static ByteBuffer putEntryHeader(final ByteBuffer buffer,
                                             final int encodedAttributeId,
                                             final int sizeOrRefId) {
      assert encodedAttributeId <= MAX_SUPPORTED_ATTRIBUTE_ID
        : "attributeId: " + encodedAttributeId + " > max " + MAX_SUPPORTED_ATTRIBUTE_ID;

      if (fitsTinyEntry(encodedAttributeId, sizeOrRefId)) {
        final byte firstByte = (byte)((encodedAttributeId << TINY_ENTRY_SIZE_BITS) | sizeOrRefId);
        return buffer.put(firstByte);
      }
      else if (fitsMediumEntry(encodedAttributeId, sizeOrRefId)) {
        final byte firstByte = (byte)(encodedAttributeId | TINY_ENTRY_MASK);
        return buffer.put(firstByte)
          .put((byte)sizeOrRefId);
      }
      else {
        final int attributeLow8Bits = encodedAttributeId & 0xFF;
        final int attributeHigh6Bits = encodedAttributeId >> 8;
        assert (attributeHigh6Bits & BIG_ENTRY_MASK) == 0 : "attributeId: " + encodedAttributeId + " is larger than possible";
        final byte firstByte = (byte)(attributeHigh6Bits | BIG_ENTRY_MASK);
        return buffer.put(firstByte)
          .put((byte)attributeLow8Bits)
          .putInt(sizeOrRefId);
      }
    }

    public static ByteBuffer putInlineEntryValue(final ByteBuffer buffer,
                                                 final byte[] newValueBytes,
                                                 final int newValueSize) {
      return buffer.put(newValueBytes, 0, newValueSize);
    }

    public static ByteBuffer putInlineEntry(final ByteBuffer buffer,
                                            final int attributeId,
                                            final byte[] newValueBytes,
                                            final int newValueSize) {
      assert buffer.remaining() >= entrySizeForInlineValueSize(attributeId, newValueSize) :
        "buffer(pos:" + buffer.position() + ", lim:" + buffer.limit() + ") " +
        "is too small for inline attribute " + newValueSize + " (+8b header)";

      putInlineEntryHeader(buffer, attributeId, newValueSize);
      return putInlineEntryValue(buffer, newValueBytes, newValueSize);
    }

    public static ByteBuffer putRefEntry(final ByteBuffer buffer,
                                         final int encodedAttributeId,
                                         final int dedicatedRecordId) {
      return putEntryHeader(buffer, encodedAttributeId,
                            INLINE_ATTRIBUTE_SMALLER_THAN + dedicatedRecordId);
    }

    public static int entrySizeForInlineValueSize(final int encodedAttributeId,
                                                  final int size) {
      return headerSizeForInline(encodedAttributeId, size) + size;
    }

    public static int headerSizeForInline(final int encodedAttributeId,
                                          final int size) {
      return headerSizeFor(encodedAttributeId, size);
    }

    public static int headerSizeForRef(final int encodedAttributeId,
                                       final int dedicatedRecordId) {
      return headerSizeFor(encodedAttributeId, INLINE_ATTRIBUTE_SMALLER_THAN + dedicatedRecordId);
    }

    private static int headerSizeFor(final int encodedAttributeId,
                                     final int sizeOrRefId) {
      if (fitsTinyEntry(encodedAttributeId, sizeOrRefId)) {
        return SMALLEST_HEADER_SIZE;
      }
      else if (fitsMediumEntry(encodedAttributeId, sizeOrRefId)) {
        return MEDIUM_HEADER_SIZE;
      }
      else {
        return BIG_HEADER_SIZE;
      }
    }

    /**
     * @return true if (encodedAttributeId, sizeOrRefId) together are small enough to fit into 'tiny'
     * entry format (1 byte for header)
     */
    private static boolean fitsTinyEntry(final int encodedAttributeId,
                                         final int sizeOrRefId) {
      return (encodedAttributeId & TINY_ENTRY_MAX_ATTRIBUTE_ID) == encodedAttributeId
             && (sizeOrRefId & TINY_ENTRY_MAX_SIZE) == sizeOrRefId;
    }

    /**
     * @return true if (encodedAttributeId, sizeOrRefId) together are small enough to fit into 'medium'
     * entry format (2 byte for header)
     */
    private static boolean fitsMediumEntry(final int encodedAttributeId,
                                           final int sizeOrRefId) {
      return (encodedAttributeId & MEDIUM_ENTRY_MAX_ATTRIBUTE_ID) == encodedAttributeId
             && (sizeOrRefId & MEDIUM_ENTRY_MAX_SIZE) == sizeOrRefId;
    }

    private static void assertMediumHeaderIsValid(final @NotNull ByteBuffer buffer,
                                                  final int entryStartOffset,
                                                  final byte firstByte) {
      assert buffer.limit() >= entryStartOffset + MEDIUM_HEADER_SIZE
        : "Invalid record(@" + entryStartOffset + ") format: " +
          "header[0](=" + Integer.toBinaryString(Byte.toUnsignedInt(firstByte)) + " -> medium record) but there is no header[1] byte. " +
          "buffer[pos: " + buffer.position() + ", lim: " + buffer.limit() + "]:  " + IOUtil.toHexString(buffer);
    }

    private static void assertBigHeaderIsValid(final @NotNull ByteBuffer buffer,
                                               final int entryStartOffset,
                                               final byte firstByte) {
      assert buffer.limit() >= entryStartOffset + BIG_HEADER_SIZE
        : "Invalid record(@" + entryStartOffset + ") format: " +
          "header[0](=" + Integer.toBinaryString(firstByte) + " -> big record) but there is no header[1..5] bytes. " +
          "buffer[pos: " + buffer.position() + ", lim: " + buffer.limit() + "]:  " + IOUtil.toHexString(buffer);
    }
  }

  private final class AttributeOutputStreamImpl extends DataOutputStream implements RepresentableAsByteArraySequence {
    private final @NotNull PersistentFSConnection connection;
    private final @NotNull FileAttribute attribute;
    private final int fileId;

    private AttributeOutputStreamImpl(final @NotNull PersistentFSConnection connection,
                                      final int fileId,
                                      final @NotNull FileAttribute attribute) {
      super(new BufferExposingByteArrayOutputStream());
      this.connection = connection;
      this.fileId = fileId;
      this.attribute = attribute;
    }

    @Override
    public void close() throws IOException {
      super.close();

      final BufferExposingByteArrayOutputStream attributeValueHolder = (BufferExposingByteArrayOutputStream)out;
      final int attributeValueSize = attributeValueHolder.size();
      checkAttributeValueSize(attribute, attributeValueSize);

      PersistentFSRecordsStorage records = connection.getRecords();
      lock.writeLock().lock();
      try {
        final int encodedAttributeId = connection.getAttributeId(attribute.getId());
        final int attributesRecordId = records.getAttributeRecordId(fileId);

        final int updatedAttributesRecordId = updateAttribute(
          attributesRecordId,
          fileId, encodedAttributeId,
          attributeValueHolder.getInternalBuffer(), attributeValueSize
        );

        //skip (updatedAttributesRecordId != attributesRecordId) check since we want to _always_ update file.modCount
        records.setAttributeRecordId(fileId, updatedAttributesRecordId);
      }
      catch (Throwable t) {
        LOG.warn("Error storing " + attribute + " of file(" + fileId + ")");
        throw FSRecords.handleError(t);
      }
      finally {
        lock.writeLock().unlock();
      }
    }

    @Override
    public @NotNull ByteArraySequence asByteArraySequence() {
      return ((BufferExposingByteArrayOutputStream)out).asByteArraySequence();
    }
  }

  //@GuardedBy("lock")
  @VisibleForTesting
  int updateAttribute(final int attributesRecordId,
                      final int fileId,
                      final int attributeId,
                      final byte[] newValueBytes,
                      final int newValueSize) throws IOException {
    checkAttributeId(attributeId);
    final int updatedAttributesRecordId;
    if (newValueSize < INLINE_ATTRIBUTE_SMALLER_THAN) {
      //if attribute value could be stored in the directory record inline
      //  -> try to (over)write it there:
      updatedAttributesRecordId = writeAttributeInlineIntoDirectoryRecord(
        attributesRecordId,
        fileId,
        attributeId,
        newValueBytes, newValueSize
      );
    }
    else {
      //value is non-inlinable
      //  -> write it to dedicated record, and add/update ref in directory record:
      updatedAttributesRecordId = writeAttributeIntoDedicatedRecord(
        attributesRecordId,
        fileId,
        attributeId,
        newValueBytes, newValueSize
      );
    }
    return updatedAttributesRecordId;
  }

  /**
   * Finds (.fileId, .attributeId) entry in storage, and overwrite its content (value) with
   * newAttributeInlinableValue bytes, either in place (if size remains the same), or re-arranging other
   * entries to find place (if size changes)
   * <p>
   * newValueSize must be < INLINE_ATTRIBUTE_SMALLER_THAN -- method does not deal with non-inlinable
   * attribute entries.
   *
   * @return attributeRecordId (same as passed in, or new one, if record was re-allocated during updating)
   */
  private int writeAttributeInlineIntoDirectoryRecord(final int attributesRecordId,
                                                      final int fileId,
                                                      final int attributeId,
                                                      final byte[] newValueBuffer,
                                                      final int newValueSize) throws IOException {
    assert newValueSize < INLINE_ATTRIBUTE_SMALLER_THAN : "Only small values could be stored in directory record";

    if (attributesRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
      //no directory record yet -> create new one:
      final int directoryRecordSize = AttributesRecord.RECORD_HEADER_SIZE
                                      + AttributeEntry.entrySizeForInlineValueSize(attributeId, newValueSize);
      return storage.writeToRecord(attributesRecordId, buffer -> {
        final ByteBuffer writeTo = ensureLimit(buffer, directoryRecordSize);
        AttributesRecord.putDirectoryRecordHeader(writeTo, fileId);
        AttributeEntry.putInlineEntry(
          writeTo,
          attributeId, newValueBuffer, newValueSize
        );

        return writeTo;
      }, directoryRecordSize);
    }
    else {//modify already existing directory record:
      final IntRef recordToDelete = new IntRef(NON_EXISTENT_ATTR_RECORD_ID);
      final int updatedAttributeRecordId = storage.writeToRecord(attributesRecordId, buffer -> {

        final AttributesRecord record = new AttributesRecord(buffer);
        if (record.findAttributeInDirectoryRecord(attributeId)) {
          //overwrite existent attribute entry:
          final AttributeEntry entryToOverwrite = record.currentEntry();
          if (!entryToOverwrite.isValueInlined()) {
            //delete dedicated record (later):
            recordToDelete.set(entryToOverwrite.dedicatedValueRecordId());
          }
          final int newEntrySize = AttributeEntry.entrySizeForInlineValueSize(attributeId, newValueSize);
          final ByteBuffer writeTo = resizeGap(buffer, entryToOverwrite.offset(),
                                               entryToOverwrite.entrySize(), newEntrySize);
          writeTo.position(entryToOverwrite.offset());
          AttributeEntry.putInlineEntry(
            writeTo,
            attributeId, newValueBuffer, newValueSize
          );

          return writeTo.position(writeTo.limit());
        }
        else {
          //no entry for attributeId -> append new entry to the end:
          final int entrySize = AttributeEntry.entrySizeForInlineValueSize(attributeId, newValueSize);
          final ByteBuffer writeTo = ensureLimitAndData(buffer, record.length + entrySize);

          writeTo.position(record.length);
          AttributeEntry.putInlineEntry(
            writeTo,
            attributeId, newValueBuffer, newValueSize
          );
          return writeTo;
        }
      });

      if (recordToDelete.get() != NON_EXISTENT_ATTR_RECORD_ID) {
        deleteRecordInStorage(recordToDelete.get());
      }

      return updatedAttributeRecordId;
    }
  }

  private int writeAttributeIntoDedicatedRecord(final int attributesRecordId,
                                                final int fileId,
                                                final int attributeId,
                                                final byte[] newValueBytes,
                                                final int newValueSize) throws IOException {
    final int updatedAttributeRecordId = storage.writeToRecord(attributesRecordId, buffer -> {
      if (buffer.limit() == 0) {
        //MAYBE RC: here we should check buffer is big enough for it, but this is quite verbose.
        //          Much better to add param to .writeToRecord(..., minRecordCapacity), and pass
        //          RECORD_HEADER_SIZE+ENTRY_HEADER_SIZE into it here

        //no directory record yet -> create directory record
        buffer.limit(AttributesRecord.RECORD_HEADER_SIZE);
        AttributesRecord.putDirectoryRecordHeader(buffer, fileId)
          .position(0);
      }

      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      if (!attributesRecord.findAttributeInDirectoryRecord(attributeId)) {
        //Directory record exists but contains no entry for attributeId
        //  -> append new entry to the end:

        //Put attribute value into dedicated record:
        final int dedicatedValueRecordId = writeDedicatedAttributeRecord(
          NON_EXISTENT_ATTR_RECORD_ID,
          fileId, attributeId,
          newValueBytes, newValueSize
        );

        //append the reference to the end of directory record:
        final ByteBuffer writeTo = ensureLimitAndData(
          buffer,
          attributesRecord.length + AttributeEntry.headerSizeForRef(attributeId, dedicatedValueRecordId)
        );
        writeTo.position(attributesRecord.length);
        AttributeEntry.putRefEntry(writeTo, attributeId, dedicatedValueRecordId);
        return writeTo;
      }
      else {
        //Directory record exists and contains the entry for attributeId
        //  -> update the entry:
        final AttributeEntry entry = attributesRecord.currentEntry();

        //Put/update attribute value into dedicated record:
        final int dedicatedValueRecordId = entry.isValueInlined() ?
                                           NON_EXISTENT_ATTR_RECORD_ID :
                                           entry.dedicatedValueRecordId();
        final int updatedDedicatedValueRecordId = writeDedicatedAttributeRecord(
          dedicatedValueRecordId,
          fileId, attributeId,
          newValueBytes, newValueSize
        );

        //The previous entry likely has a different size than the future (reference) entry: we need to
        // either collapse or expand it:
        final int refEntrySize = AttributeEntry.headerSizeForRef(attributeId, updatedDedicatedValueRecordId);

        final ByteBuffer writeTo = resizeGap(buffer, entry.offset(),
                                             entry.entrySize(), refEntrySize);
        writeTo.position(entry.offset());

        AttributeEntry.putRefEntry(writeTo, attributeId, updatedDedicatedValueRecordId);

        return writeTo.position(writeTo.limit());
      }
    });
    return updatedAttributeRecordId;
  }


  //@GuardedBy("lock")
  @VisibleForTesting
  byte[] readAttributeValue(final int attributesRecordId,
                            final int fileId,
                            final int attributeId) throws IOException {
    return storage.readRecord(attributesRecordId, buffer -> {
      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      attributesRecord.checkBackrefFile(attributesRecordId, fileId);


      if (!attributesRecord.findAttributeInDirectoryRecord(attributeId)) {
        return null;
      }

      final AttributeEntry attributeEntry = attributesRecord.currentEntry();
      if (attributeEntry.isValueInlined()) {
        return attributeEntry.inlinedValueAsByteArray();
      }

      final int dedicatedRecordId = attributeEntry.dedicatedValueRecordId();
      return storage.readRecord(
        dedicatedRecordId,
        dedicatedRecordBuffer -> readDedicatedRecordPayload(
          attributesRecordId, fileId, attributeEntry.attributeId,
          dedicatedRecordBuffer
        )
      );
    });
  }

  //@GuardedBy("lock")
  @VisibleForTesting
  <R> R readAttributeValue(final int attributesRecordId,
                           final int fileId,
                           final int attributeId,
                           final ByteBufferReader<R> reader) throws IOException {
    return storage.readRecord(attributesRecordId, buffer -> {
      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      attributesRecord.checkBackrefFile(attributesRecordId, fileId);

      if (!attributesRecord.findAttributeInDirectoryRecord(attributeId)) {
        return null;
      }

      final AttributeEntry attributeEntry = attributesRecord.currentEntry();
      if (attributeEntry.isValueInlined()) {
        return reader.read(attributeEntry.inlinedValueAsSlice());
      }

      final int dedicatedRecordId = attributeEntry.dedicatedValueRecordId();
      return storage.readRecord(
        dedicatedRecordId,
        dedicatedRecordBuffer -> reader.read(
          readDedicatedRecordPayloadAsSlice(
            attributesRecordId, fileId, attributeEntry.attributeId,
            dedicatedRecordBuffer
          )
        )
      );
    });
  }


  //@GuardedBy("lock")
  @VisibleForTesting
  boolean hasAttribute(final int attributesRecordId,
                       final int fileId,
                       final int attributeId) throws IOException {
    if (!storage.hasRecord(attributesRecordId)) {
      return false;
    }
    return storage.readRecord(attributesRecordId, buffer -> {
      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      attributesRecord.checkBackrefFile(attributesRecordId, fileId);
      if (!attributesRecord.hasDirectory()) {
        throw new IllegalArgumentException("record(" + attributesRecordId + ") is not a directory record: " +
                                           "(" + attributesRecord.backRefFileId + ", " + attributesRecord.dedicatedAttributeId + ")");
      }
      if (!attributesRecord.findAttributeInDirectoryRecord(attributeId)) {
        return false;
      }

      final AttributeEntry attributeEntry = attributesRecord.currentEntry();
      if (attributeEntry.isValueInlined()) {
        return true;
      }
      else {
        final int dedicatedRecordId = attributeEntry.dedicatedValueRecordId();
        return storage.hasRecord(dedicatedRecordId);
      }
    });
  }

  //@GuardedBy("lock")
  @VisibleForTesting
  boolean deleteAttributes(final int attributesRecordId,
                           final int fileId) throws IOException {
    if (attributesRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
      return false;
    }

    try {
      //Use writeToRecord even though we only read is a trick to avoid deadlock: we call .deleteRecord()
      // inside callback, which tries to acquire storage write lock, which could lead to deadlock
      // if storage readLock is already acquired, even by current thread (RW lock not allow read->write
      // lock escalation).
      storage.writeToRecord(attributesRecordId, buffer -> {
        final AttributesRecord attributesRecord = new AttributesRecord(buffer);
        attributesRecord.checkBackrefFile(attributesRecordId, fileId);
        for (final AttributeEntry entry = attributesRecord.currentEntry();
             entry.isValid();
             entry.moveToNextEntry()) {
          if (!entry.isValueInlined()) {
            deleteRecordInStorage(entry.dedicatedValueRecordId());
            //TODO clear entry.dedicatedRecordId?
          }
        }
        return null;//indicate no actual write happened
      });
    }
    catch (RecordAlreadyDeletedException ex) {
      if (IGNORE_ALREADY_DELETED_ERRORS) {
        LOG.warn("Record [" + attributesRecordId + "] is already deleted -> likely improper app shutdown?");
      }
      else {
        throw ex;
      }
    }

    return deleteRecordInStorage(attributesRecordId);
  }

  private boolean deleteRecordInStorage(int recordId) throws IOException {
    try {
      storage.deleteRecord(recordId);
      return true;
    }
    catch (RecordAlreadyDeletedException ex) {
      if (IGNORE_ALREADY_DELETED_ERRORS) {
        LOG.warn("Record [" + recordId + "] is already deleted -> likely improper app shutdown?");
        return false;
      }
      else {
        throw ex;
      }
    }
  }


  private static byte[] readDedicatedRecordPayload(final int dedicatedAttributeRecordId,
                                                   final int fileId,
                                                   final int attributeId,
                                                   final ByteBuffer dedicatedRecordBuffer) throws IOException {
    if (dedicatedRecordBuffer.remaining() < AttributesRecord.DEDICATED_RECORD_HEADER_SIZE) {
      throw new CorruptedException(
        "record(" + dedicatedAttributeRecordId + ", fileId: " + fileId + ", " + attributeId + ") is too short for dedicated record: " +
        dedicatedRecordBuffer.remaining() + " b in buffer < " + AttributesRecord.DEDICATED_RECORD_HEADER_SIZE + " b header"
      );
    }
    final int backRefFileId = -dedicatedRecordBuffer.getInt(AttributesRecord.RECORD_FILE_ID_OFFSET);
    final int backRefAttributeId = dedicatedRecordBuffer.getInt(AttributesRecord.DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET);
    if (backRefFileId != fileId) {
      throw new CorruptedException("record(" + dedicatedAttributeRecordId + ").fileId(" + fileId + ") " +
                                   "!= backref fileId(" + backRefFileId + "), buffer remains: " +
                                   IOUtil.toHexString(dedicatedRecordBuffer)
      );
    }
    if (backRefAttributeId != attributeId) {
      throw new CorruptedException("record(" + dedicatedAttributeRecordId + ").attributeId(" + attributeId + ") " +
                                   "!= backref attributeId(" + backRefAttributeId + "), buffer remains: " +
                                   IOUtil.toHexString(dedicatedRecordBuffer)
      );
    }

    final int valueLength = dedicatedRecordBuffer.remaining() - AttributesRecord.DEDICATED_RECORD_HEADER_SIZE;
    final byte[] entryValue = new byte[valueLength];
    dedicatedRecordBuffer.get(AttributesRecord.DEDICATED_RECORD_HEADER_SIZE, entryValue);
    return entryValue;
  }

  private static ByteBuffer readDedicatedRecordPayloadAsSlice(final int dedicatedAttributeRecordId,
                                                              final int fileId,
                                                              final int attributeId,
                                                              final ByteBuffer dedicatedRecordBuffer) throws IOException {
    final int backRefFileId = -dedicatedRecordBuffer.getInt(AttributesRecord.RECORD_FILE_ID_OFFSET);
    final int backRefAttributeId = dedicatedRecordBuffer.getInt(AttributesRecord.DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET);
    if (backRefFileId != fileId) {
      throw new CorruptedException("record(" + dedicatedAttributeRecordId + ").fileId(" + fileId + ") " +
                                   "!= backref fileId(" + backRefFileId + "), buffer remains: " +
                                   IOUtil.toHexString(dedicatedRecordBuffer)
      );
    }
    if (backRefAttributeId != attributeId) {
      throw new CorruptedException("record(" + attributeId + ").attributeId(" + attributeId + ") " +
                                   "!= backref attributeId(" + backRefAttributeId + "), buffer remains: " +
                                   IOUtil.toHexString(dedicatedRecordBuffer)
      );
    }

    final int valueLength = dedicatedRecordBuffer.remaining() - AttributesRecord.DEDICATED_RECORD_HEADER_SIZE;
    return dedicatedRecordBuffer.slice(AttributesRecord.DEDICATED_RECORD_HEADER_SIZE, valueLength)
      .order(dedicatedRecordBuffer.order());
  }


  private int writeDedicatedAttributeRecord(final int dedicatedAttributeRecordId,
                                            final int fileId,
                                            final int attributeId,
                                            final byte[] newValueBytes,
                                            final int newValueSize) throws IOException {
    return storage.writeToRecord(dedicatedAttributeRecordId, buffer -> {
      final ByteBuffer toWrite = ensureLimit(buffer, AttributesRecord.dedicatedRecordSizeForValueSize(newValueSize));
      return AttributesRecord
        .putDedicatedValueRecordHeader(toWrite, fileId, attributeId)
        .put(newValueBytes, 0, newValueSize);
    });
  }

  /**
   * @return buffer with the limit rose up to requiredLimit, if currently it is lower. If
   * buffer capacity is not enough for requiredLimit -- new buffer is allocated with capacity=requiredLimit,
   * and set position to buffer.position, and limit to requiredLimit. Data from buffer is not copied,
   * use {@link #ensureLimitAndData(ByteBuffer, int)} for that.
   */
  private static @NotNull ByteBuffer ensureLimit(final ByteBuffer buffer,
                                                 final int requiredLimit) {
    if (buffer.capacity() >= requiredLimit) {
      return buffer.limit(Math.max(buffer.limit(), requiredLimit));
    }
    else {
      return ByteBuffer.allocate(requiredLimit)
        .order(buffer.order())
        .position(buffer.position());
    }
  }

  /**
   * @return buffer with limit set to at least requiredLimit. If buffer.limit() already more than
   * requiredLimit -- do nothing. If buffer.capacity() is not big enough for requiredLimit -- method
   * returns new buffer with same content & same position as the old one, and limit=capacity=requiredLimit.
   */
  private static ByteBuffer ensureLimitAndData(final ByteBuffer buffer,
                                               final int requiredLimit) {
    if (buffer.capacity() >= requiredLimit) {
      return buffer.limit(Math.max(buffer.limit(), requiredLimit));
    }
    else {
      return ByteBuffer.allocate(requiredLimit)
        .order(buffer.order())
        .put(0, buffer, 0, buffer.limit())
        .position(buffer.position());
    }
  }


  /**
   * Method 'resizes' a gap in buffer. Imagine you want to replace a few bytes in the middle
   * of the buffer with another few bytes, of different size -- you need to ensure everything
   * else around those few bytes moved accordingly. This method transforms buffer[...offset..(offset+oldGapSize)...]
   * into buffer[...offset..(offset+newGapSize)...] keeping data before & after the gap intact.
   */
  @VisibleForTesting
  static ByteBuffer resizeGap(final ByteBuffer buffer,
                              final int offset,
                              final int oldGapSize,
                              final int newGapSize) {
    final int oldGapEndOffset = offset + oldGapSize;
    final int newGapEndOffset = offset + newGapSize;
    final int limitBefore = buffer.limit();
    if (newGapSize > oldGapSize) {//expand:
      final ByteBuffer enlargedBuffer = ensureLimitAndData(buffer, limitBefore + (newGapSize - oldGapSize));
      enlargedBuffer.put(newGapEndOffset,
                         buffer, oldGapEndOffset, limitBefore - oldGapEndOffset);
      enlargedBuffer.limit(limitBefore + newGapSize - oldGapSize);
      return enlargedBuffer;
    }
    else if (newGapSize < oldGapSize) {//collapse:
      buffer.put(newGapEndOffset,
                 buffer, oldGapEndOffset, limitBefore - oldGapEndOffset);
      buffer.limit(limitBefore + (newGapSize - oldGapSize));
      return buffer;
    }
    else {//nothing to move: oldGap=newGap
      return buffer;
    }
  }

  private static void checkAttributeId(final int attributeId) {
    if (attributeId < 0 || attributeId > MAX_SUPPORTED_ATTRIBUTE_ID) {
      throw new IllegalArgumentException(
        "attributeId(=" + attributeId + ") must be in [0.." + MAX_SUPPORTED_ATTRIBUTE_ID + "]");
    }
  }
}
