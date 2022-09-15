// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 */
public class AttributesStorageOnTheTopOfBlobStorage extends AbstractAttributesStorage {
  //Persistent format (see AttributesRecord/AttributeEntry):
  //  attributes directory record: header, entry*
  //      header:  fileId[4b]   (ref back to file owned attributes)
  //      entry:  attributeId[4b], inlineSizeOrRefId[4b], inlineData[inlineSizeOrRefId]?
  //
  //      Attribute values < INLINE_ATTRIBUTE_MAX_SIZE stored inline, size in .inlineSizeOrRefId,
  //      Attribute values > INLINE_ATTRIBUTE_MAX_SIZE stored in dedicated records, with
  //      dedicatedRecordId = (inlineSizeOrRefId-INLINE_ATTRIBUTE_MAX_SIZE), inlineData is absent
  //      for them.
  //
  //  attribute dedicated record: header, data[...]
  //      header: -fileId[4b], attributeId[4b]
  //               (fileId refs back to file owned attribute, '-' distinguishes dedicated record
  //               from directory record)
  //      data[...]: attribute value, size = record.length-header.size(8)

  @NotNull
  private final StreamlinedBlobStorage storage;

  private final AtomicInteger modCount = new AtomicInteger();

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public AttributesStorageOnTheTopOfBlobStorage(final @NotNull StreamlinedBlobStorage storage) { this.storage = storage; }

  @Override
  public int getVersion() throws IOException {
    return storage.getDataFormatVersion();
  }

  @Override
  public void setVersion(final int version) throws IOException {
    storage.setDataFormatVersion(version);
  }

  @Override
  public @Nullable AttributeInputStream readAttribute(final PersistentFSConnection connection,
                                                      final int fileId,
                                                      final @NotNull FileAttribute attribute) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.readLock().lock();
    try {
      final int attributeRecordId = connection.getRecords().getAttributeRecordId(fileId);
      if (attributeRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return null;
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

  @Override
  public boolean hasAttributePage(final PersistentFSConnection connection,
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
  public @NotNull AttributeOutputStream writeAttribute(final PersistentFSConnection connection,
                                                       final int fileId,
                                                       final @NotNull FileAttribute attribute) {
    return new AttributeOutputStream(
      new AttributeOutputStreamImpl(connection, fileId, attribute),
      connection.getEnumeratedAttributes()
    );
  }

  @Override
  public void deleteAttributes(final PersistentFSConnection connection,
                               final int fileId) throws IOException {
    PersistentFSConnection.ensureIdIsValid(fileId);
    lock.writeLock().lock();
    try {
      final int attributeRecordId = connection.getRecords().getAttributeRecordId(fileId);
      deleteAttributes(attributeRecordId, fileId);
    }
    finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void checkAttributesStorageSanity(final PersistentFSConnection connection,
                                           final int fileId,
                                           final @NotNull IntList usedAttributeRecordIds,
                                           final @NotNull IntList validAttributeIds) throws IOException {
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  @Override
  public int getLocalModificationCount() {
    return modCount.get();
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

  public static boolean deleteStorageFiles(final Path file) throws IOException {
    return Files.deleteIfExists(file);
  }

  /**
   * 'get' methods are all use absolute buffer access methods, hence don't move buffer position/limit,
   * but 'put' methods use relative buffer access, and move buffer position (because this is that expected
   * by calling code)
   */
  protected static class AttributesRecord {

    public static final int RECORD_FILE_ID_OFFSET = 0;
    public static final int RECORD_HEADER_SIZE = Integer.BYTES;

    public static final int DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET = RECORD_FILE_ID_OFFSET + Integer.BYTES;
    public static final int DEDICATED_RECORD_HEADER_SIZE = DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET + Integer.BYTES;

    private final ByteBuffer buffer;
    private final int length;

    private final int backRefFileId;
    private final int encodedAttributeId;


    private final AttributeEntry entry = new AttributeEntry();

    public AttributesRecord(final ByteBuffer buffer) {
      this.buffer = buffer;
      this.length = buffer.remaining();

      if (length > RECORD_HEADER_SIZE) {
        final int fileId = buffer.getInt(RECORD_FILE_ID_OFFSET);
        if (fileId < 0) {
          //this is dedicated attribute record, not directory record
          backRefFileId = -fileId;
          encodedAttributeId = buffer.getInt(DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET);
        }
        else {
          backRefFileId = fileId;
          encodedAttributeId = -1;
        }
        entry.reset(RECORD_HEADER_SIZE, buffer);
      }
      else {
        backRefFileId = -1;
        encodedAttributeId = -1;
      }
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

    public boolean moveToNextEntry() {
      return entry.moveToNextEntry();
    }

    public boolean hasDirectory() {
      return length >= RECORD_HEADER_SIZE;
    }

    public boolean hasDedicatedAttribute() {
      return length >= DEDICATED_RECORD_HEADER_SIZE
             && encodedAttributeId > 0;
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

  protected static class AttributeEntry {
    public static final int ENTRY_ATTR_ID_OFFSET = 0;
    public static final int ENTRY_SIZE_OR_REF_OFFSET = Integer.BYTES;
    public static final int ENTRY_INLINE_VALUE_OFFSET = ENTRY_SIZE_OR_REF_OFFSET + Integer.BYTES;
    public static final int ENTRY_HEADER_SIZE = ENTRY_SIZE_OR_REF_OFFSET + Integer.BYTES;

    private int entryStartOffset;
    private int attributeId;
    private int inlinedValueSizeOrDedicatedRecordId;
    private ByteBuffer buffer;

    public void reset(final int entryStartOffset,
                      final ByteBuffer buffer) {
      this.entryStartOffset = entryStartOffset;
      this.buffer = buffer;

      if (isValid()) {
        attributeId = buffer.getInt(entryStartOffset + ENTRY_ATTR_ID_OFFSET);
        inlinedValueSizeOrDedicatedRecordId = buffer.getInt(entryStartOffset + ENTRY_SIZE_OR_REF_OFFSET);
      }
      else {
        attributeId = -1;
        inlinedValueSizeOrDedicatedRecordId = -1;
      }
    }

    public boolean isValid() {
      return entryStartOffset + ENTRY_HEADER_SIZE <= buffer.limit();
    }

    public int attributeId() {
      return attributeId;
    }

    public boolean isValueInlined() {
      return inlinedValueSizeOrDedicatedRecordId < INLINE_ATTRIBUTE_MAX_SIZE;
    }

    public int dedicatedValueRecordId() {
      return inlinedValueSizeOrDedicatedRecordId - INLINE_ATTRIBUTE_MAX_SIZE;
    }

    public int inlinedValueStartOffset() {
      return entryStartOffset + ENTRY_INLINE_VALUE_OFFSET;
    }

    public int inlinedValueLength() {
      return isValueInlined() ? inlinedValueSizeOrDedicatedRecordId : 0;
    }

    public int offset() {
      return entryStartOffset;
    }

    public int nextEntryOffset() {
      return entryStartOffset + ENTRY_HEADER_SIZE +
             (isValueInlined() ? inlinedValueLength() : 0);
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

    public byte[] inlinedValue() {
      final byte[] entryValue = new byte[inlinedValueLength()];
      buffer.get(inlinedValueStartOffset(), entryValue);
      return entryValue;
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
      assert newValueSize <= INLINE_ATTRIBUTE_MAX_SIZE : newValueSize + " > " + INLINE_ATTRIBUTE_MAX_SIZE;
      return buffer.putInt(encodedAttributeId)
        .putInt(newValueSize);
    }

    public static ByteBuffer putInlineEntryValue(final ByteBuffer buffer,
                                                 final byte[] newValueBytes,
                                                 final int newValueSize) {
      return buffer.put(newValueBytes, 0, newValueSize);
    }

    public static ByteBuffer putInlineEntry(final ByteBuffer buffer,
                                            final int attributeId,
                                            final int newValueSize,
                                            final byte[] newValueBytes) {
      putInlineEntryHeader(buffer, attributeId, newValueSize);
      return putInlineEntryValue(buffer, newValueBytes, newValueSize);
    }

    public static ByteBuffer putRefEntry(final ByteBuffer buffer,
                                         final int attributeId,
                                         final int dedicatedRecordId) {
      return buffer.putInt(attributeId)
        .putInt(INLINE_ATTRIBUTE_MAX_SIZE + dedicatedRecordId);
    }

    public static int entrySizeForValueSize(final int size) {
      return ENTRY_HEADER_SIZE + size;
    }
  }

  private final class AttributeOutputStreamImpl extends DataOutputStream {
    @NotNull
    private final PersistentFSConnection connection;
    @NotNull
    private final FileAttribute attribute;
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
      lock.writeLock().lock();
      try {
        final int encodedAttributeId = connection.getAttributeId(attribute.getId());
        final int attributesRecordId = connection.getRecords().getAttributeRecordId(fileId);
        final BufferExposingByteArrayOutputStream valueHolder = (BufferExposingByteArrayOutputStream)out;

        final int updatedAttributesRecordId = updateAttribute(
          attributesRecordId,
          fileId, encodedAttributeId,
          valueHolder.getInternalBuffer(), valueHolder.size()
        );

        if (updatedAttributesRecordId != attributesRecordId) {
          connection.getRecords().setAttributeRecordId(fileId, updatedAttributesRecordId);
        }
        modCount.incrementAndGet();
      }
      catch (Throwable t) {
        FSRecords.handleError(t);
        throw new RuntimeException(t);
      }
      finally {
        lock.writeLock().unlock();
      }
    }
  }

  @VisibleForTesting
  //@GuardedBy("lock")
  protected int updateAttribute(final int attributesRecordId,
                                final int fileId,
                                final int attributeId,
                                final byte[] newValueBytes,
                                final int newValueSize) throws IOException {
    final int updatedAttributesRecordId;
    if (newValueSize < INLINE_ATTRIBUTE_MAX_SIZE) {
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
   * entries to put updated one in the end (if size was changed)
   * <p>
   * newValueSize must be < MAX_SMALL_ATTR_SIZE -- method does not deal with non-inlinable
   * attribute entries.
   * <p>
   * if newValueSize==0 then attribute entry is removed from directory record
   * FIXME RC: is above sentence is still true? seems like it is copied from the old version
   *
   * @return attributeRecordId (same as passed in, or new one, if record was re-allocated during updating)
   */
  private int writeAttributeInlineIntoDirectoryRecord(final int attributesRecordId,
                                                      final int fileId,
                                                      final int attributeId,
                                                      final byte[] newValueBuffer,
                                                      final int newValueSize) throws IOException {
    assert newValueSize < INLINE_ATTRIBUTE_MAX_SIZE : "Only small values could be stored in directory record";

    if (attributesRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
      //no directory record yet -> create new one:
      return storage.writeToRecord(attributesRecordId, buffer -> {
        final int directoryRecordSize = AttributesRecord.RECORD_HEADER_SIZE
                                        + AttributeEntry.entrySizeForValueSize(newValueSize);
        final ByteBuffer writeTo = ensureLimit(buffer, directoryRecordSize);
        AttributesRecord.putDirectoryRecordHeader(writeTo, fileId);
        AttributeEntry.putInlineEntry(
          writeTo,
          attributeId, newValueSize,
          newValueBuffer
        );

        return writeTo;
      });
    }
    else {//modify already existing directory record:
      final int[] recordToDelete = {NON_EXISTENT_ATTR_RECORD_ID};
      final int updatedAttributeRecordId = storage.writeToRecord(attributesRecordId, buffer -> {

        final AttributesRecord record = new AttributesRecord(buffer);
        if (record.findAttributeInDirectoryRecord(attributeId)) {
          //overwrite existent attribute entry:
          final AttributeEntry entryToOverwrite = record.currentEntry();
          if (!entryToOverwrite.isValueInlined()) {
            //delete dedicated record (later):
            recordToDelete[0] = entryToOverwrite.dedicatedValueRecordId();
          }
          //is buffer.capacity enough?
          final int sizeDiff = newValueSize - entryToOverwrite.inlinedValueLength();
          final ByteBuffer writeTo = ensureLimit(buffer, record.length + sizeDiff);

          //there are a lot of bytes movements below, and it is easier to just 'switch checks off'
          // (set .limit=.capacity) here, and return bounds checks before returning from the method,
          // than to reason about .limit in each case.
          writeTo.limit(writeTo.capacity());

          if (writeTo != buffer) {
            writeTo.put(0,
                        buffer, 0, entryToOverwrite.offset());
          }

          if (sizeDiff != 0) {
            //shift all entries _after_ current one:
            final int remainingEntriesLength = record.length - entryToOverwrite.nextEntryOffset();
            writeTo.put(
              entryToOverwrite.nextEntryOffset() + sizeDiff,
              buffer,
              entryToOverwrite.nextEntryOffset(), remainingEntriesLength
            );
          }
          writeTo.position(entryToOverwrite.offset());
          AttributeEntry.putInlineEntry(
            writeTo,
            attributeId, newValueSize,
            newValueBuffer
          );

          return writeTo
            .limit(record.length + sizeDiff)
            .position(record.length + sizeDiff);
        }
        else {
          //no entry for attributeId -> append new entry to the end:
          final int entrySize = AttributeEntry.entrySizeForValueSize(newValueSize);
          final ByteBuffer writeTo = ensureLimitAndData(buffer, record.length + entrySize);

          writeTo.position(record.length);
          AttributeEntry.putInlineEntry(
            buffer,
            attributeId, newValueSize,
            newValueBuffer
          );
          return writeTo;
        }
      });

      if (recordToDelete[0] != NON_EXISTENT_ATTR_RECORD_ID) {
        storage.deleteRecord(recordToDelete[0]);
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
        //TODO RC: here we should check buffer is big enough for it, but this is quite verbose.
        //         Much better to add param to .writeToRecord(..., minRecordCapacity), and pass
        //         RECORD_HEADER_SIZE+ENTRY_HEADER_SIZE into it here

        //no directory record yet -> create directory record
        buffer.limit(AttributesRecord.RECORD_HEADER_SIZE);
        AttributesRecord.putDirectoryRecordHeader(buffer, fileId)
          .position(0);
      }

      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      if (!attributesRecord.findAttributeInDirectoryRecord(attributeId)) {
        //There is a directory record, but no entry for attributeId in it yet
        //  -> append new entry to the end:

        //Put attribute value into dedicated record:
        final int dedicatedValueRecordId = writeDedicatedAttributeRecord(
          NON_EXISTENT_ATTR_RECORD_ID,
          fileId, attributeId,
          newValueBytes, newValueSize
        );

        final ByteBuffer toWrite = ensureLimitAndData(buffer, attributesRecord.length + AttributeEntry.ENTRY_HEADER_SIZE);
        toWrite.position(attributesRecord.length);
        AttributeEntry.putRefEntry(toWrite, attributeId, dedicatedValueRecordId);
        return toWrite;
      }
      else {
        //There is a directory record, and entry for attributeId in it -> update the entry
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

        buffer.position(entry.offset());
        AttributeEntry.putRefEntry(buffer, attributeId, updatedDedicatedValueRecordId);
        if (entry.isValueInlined()) {
          //'collapse' inlined value (we need only entry header for dedicated value ref)
          buffer.put(entry.inlinedValueStartOffset(),
                     buffer, entry.nextEntryOffset(), buffer.limit() - entry.nextEntryOffset());
          buffer.limit(buffer.limit() - entry.inlinedValueLength());
        }
        return buffer.position(buffer.limit());
      }
    });
    return updatedAttributeRecordId;
  }


  @VisibleForTesting
  //@GuardedBy("lock")
  protected byte[] readAttributeValue(final int attributesRecordId,
                                      final int fileId,
                                      final int attributeId) throws IOException {
    final byte[] attributeValueBytes = storage.readRecord(attributesRecordId, buffer -> {
      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      assert attributesRecord.backRefFileId == fileId : "record(" + attributesRecordId + ").fileId(" + fileId + ")" +
                                                        " != backref fileId(" + attributesRecord.backRefFileId + ")";

      if (!attributesRecord.findAttributeInDirectoryRecord(attributeId)) {
        return null;
      }

      final AttributeEntry attributeEntry = attributesRecord.currentEntry();
      if (attributeEntry.isValueInlined()) {
        return attributeEntry.inlinedValue();
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
    return attributeValueBytes;
  }

  @VisibleForTesting
  //@GuardedBy("lock")
  protected boolean hasAttribute(final int attributesRecordId,
                                 final int fileId,
                                 final int attributeId) throws IOException {
    if (!storage.hasRecord(attributesRecordId)) {
      return false;
    }
    return storage.readRecord(attributesRecordId, buffer -> {
      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      assert attributesRecord.backRefFileId == fileId : "record(" + attributesRecordId + ").fileId(" + fileId + ")" +
                                                        " != backref fileId(" + attributesRecord.backRefFileId + ")";
      if (!attributesRecord.hasDirectory()) {
        throw new IllegalArgumentException("record(" +
                                           attributesRecordId +
                                           ") is not a directory record (" +
                                           attributesRecord.backRefFileId +
                                           ", " +
                                           attributesRecord.encodedAttributeId +
                                           ")");
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

  @VisibleForTesting
  //@GuardedBy("lock")
  protected boolean deleteAttributes(final int attributesRecordId,
                                     final int fileId) throws IOException {
    if (attributesRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
      return false;
    }
    //Use writeToRecord even though we only read is a trick to avoid deadlock: we call .deleteRecord()
    // inside callback, which tries to acquire storage write lock, which could lead to deadlock
    // if storage readLock is already acquired, even by current thread (RW lock not allow read->write
    // lock escalation).
    storage.writeToRecord(attributesRecordId, buffer -> {
      final AttributesRecord attributesRecord = new AttributesRecord(buffer);
      assert attributesRecord.backRefFileId == fileId : "record(" + attributesRecordId + ").fileId(" + fileId + ")" +
                                                        " != backref fileId(" + attributesRecord.backRefFileId + ")";
      for (final AttributeEntry entry = attributesRecord.currentEntry();
           entry.isValid();
           entry.moveToNextEntry()) {
        if (!entry.isValueInlined()) {
          storage.deleteRecord(entry.dedicatedValueRecordId());
        }
      }
      return null;//indicate no actual write happened
    });

    storage.deleteRecord(attributesRecordId);

    return true;
  }


  private static byte[] readDedicatedRecordPayload(final int dedicatedAttributeRecordId,
                                                   final int fileId,
                                                   final int attributeId,
                                                   final ByteBuffer dedicatedRecordBuffer) {
    final int backRefFileId = -dedicatedRecordBuffer.getInt(AttributesRecord.RECORD_FILE_ID_OFFSET);
    final int backRefAttributeId = dedicatedRecordBuffer.getInt(AttributesRecord.DEDICATED_RECORD_ATTRIBUTE_ID_OFFSET);
    assert backRefFileId == fileId : "record(" + dedicatedAttributeRecordId + ").fileId(" + fileId + ") " +
                                     "!= backref fileId(" + backRefFileId + ")";
    assert backRefAttributeId == attributeId : "record(" + attributeId + ").attributeId(" + attributeId + ") " +
                                               "!= backref attributeId(" + backRefAttributeId + ")";

    final int valueLength = dedicatedRecordBuffer.remaining() - AttributesRecord.DEDICATED_RECORD_HEADER_SIZE;
    final byte[] entryValue = new byte[valueLength];
    dedicatedRecordBuffer.get(AttributesRecord.DEDICATED_RECORD_HEADER_SIZE, entryValue);
    return entryValue;
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
   * and set position to buffer.position, and limit to requiredLimit. Data from buffer is not copied
   * use {@link #ensureLimitAndData(ByteBuffer, int)} for that.
   */
  @NotNull
  private static ByteBuffer ensureLimit(final ByteBuffer buffer,
                                        final int requiredLimit) {
    if (buffer.capacity() >= requiredLimit) {
      //TODO shouln't do that?
      return buffer.limit(Math.max(buffer.limit(), requiredLimit));
    }
    else {
      return ByteBuffer.allocate(requiredLimit)
        .position(buffer.position())
        .limit(requiredLimit)
        .order(buffer.order());
    }
  }

  private static ByteBuffer ensureLimitAndData(final ByteBuffer buffer,
                                               final int requiredCapacity) {
    if (buffer.capacity() >= requiredCapacity) {
      return buffer.limit(Math.max(buffer.limit(), requiredCapacity));
    }
    else {
      return ByteBuffer.allocate(requiredCapacity)
        .order(buffer.order())
        .put(0, buffer, buffer.position(), buffer.limit());
    }
  }
}
