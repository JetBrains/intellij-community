// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.platform.util.io.storages.blobstorage.RecordAlreadyDeletedException;
import com.intellij.util.IntPair;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase.AttributeRecord.newAttributeRecord;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSAttributesStorage.INLINE_ATTRIBUTE_SMALLER_THAN;
import static com.intellij.openapi.vfs.newvfs.persistent.VFSAttributesStorage.NON_EXISTENT_ATTRIBUTE_RECORD_ID;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AttributesStorageOnTheTopOfBlobStorageTestBase {

  protected static final int PAGE_SIZE = 1 << 22;

  /** Not so many records because each of them could be up to 800k, which leads to OoM quite quickly */
  protected static final int ENOUGH_RECORDS = 1 << 15;
  /** Records # to try for large-records tests */
  protected static final int ENOUGH_BIG_RECORDS = 1 << 10;

  protected static final int ARBITRARY_FILE_ID = 157;
  protected static final int ARBITRARY_ATTRIBUTE_ID = AttributesStorageOverBlobStorage.MAX_SUPPORTED_ATTRIBUTE_ID - 1;


  @BeforeClass
  public static void beforeClass() throws Exception {
    IndexDebugProperties.DEBUG = true;
  }

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();


  protected Path storagePath;
  protected StreamlinedBlobStorage storage;

  protected AttributesStorageOverBlobStorage attributesStorage;

  protected Attributes attributes;

  @Before
  public void setUp() throws Exception {
    attributes = new Attributes();

    storagePath = temporaryFolder.newFile().toPath();
    attributesStorage = openAttributesStorage(storagePath);
  }

  @After
  public void tearDown() throws Exception {
    closeStorage();
  }

  @Test
  public void nonInsertedRecordIsNotExistsInStorage() throws IOException {
    int nonInsertedRecordId = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
    AttributeRecord record = new AttributeRecord(nonInsertedRecordId, ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(16);
    assertFalse(
      "Not inserted record is not exists",
      record.existsInStorage(attributesStorage)
    );
  }

  @Test
  public void singleSmallRecordInserted_AreAllReportedExistInStorage_AndCouldBeReadBack() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN - 1);

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      attributes.existsInStorage(insertedRecord, attributesStorage)
    );

    assertArrayEquals(
      "Attribute content could be read back as-is",
      insertedRecord.attributeBytes(),
      insertedRecord.readValueFromStorage(attributesStorage)
    );
  }

  @Test
  public void singleSmallRecordInserted_AreAllReportedExistInStorage_AndCouldBeReadBackRaw() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN - 1);

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      attributes.existsInStorage(insertedRecord, attributesStorage)
    );

    assertArrayEquals(
      "Attribute content could be read back as-is",
      insertedRecord.attributeBytes(),
      insertedRecord.readValueFromStorageRaw(attributesStorage)
    );
  }

  @Test
  public void singleBigRecordInserted_ReportedExistInStorage_AndCouldBeReadBack() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(/*INLINE_ATTRIBUTE_SMALLER_THAN + 1*/maxAttributeValueSizeToTest());

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      insertedRecord.existsInStorage(attributesStorage)
    );

    assertArrayEquals(
      "Attribute content could be read back as-is",
      insertedRecord.attributeBytes(),
      insertedRecord.readValueFromStorage(attributesStorage)
    );
  }

  @Test
  public void singleOverlyBigRecordInserted_FailsToBeInsert() throws IOException {
    //Check attributeSize > max supported record size of the storage:
    //maxAttributeValueSizeToTest() is too safe: it is guaranteed to be < max supported value of the storage, but
    // it is not guaranteed to be exactly == max supported value of the storage => use _underlying_ storage
    // .maxPayloadSupported() -- it is guaranteed to be overflow, since attributes storage adds its own record
    // header:
    int maxPayloadSupported = storage.maxPayloadSupported();
    for (int overlyBigAttributeSize : new int[]{
      maxPayloadSupported + 1,
      maxPayloadSupported * 3 / 2,
      maxPayloadSupported * 2}) {
      AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
        .withRandomAttributeBytes(overlyBigAttributeSize);

      try {
        attributes.insertOrUpdateRecord(record, attributesStorage);
        fail("Attribute(" + overlyBigAttributeSize + "b) > max supported size => must rejected");
      }
      catch (FileTooBigException ignore) {
        //OK, expected
      }
    }
  }

  @Test
  public void singleBigRecordInserted_ReportedExistInStorage_AndCouldBeReadBackRaw() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(/*INLINE_ATTRIBUTE_SMALLER_THAN + 1*/maxAttributeValueSizeToTest());

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      insertedRecord.existsInStorage(attributesStorage)
    );

    assertArrayEquals(
      "Attribute content could be read back as-is",
      insertedRecord.attributeBytes(),
      insertedRecord.readValueFromStorageRaw(attributesStorage)
    );
  }

  @Test
  public void singleBigRecordInserted_ReportedExistInStorage_AndCouldBeReadBack_WithForEach() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(/*INLINE_ATTRIBUTE_SMALLER_THAN + 1*/ maxAttributeValueSizeToTest());

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    Long2ObjectMap<AttributeRecord> recordsReadWithForEach = readAllRecordsWithForEach(attributesStorage);
    assertEquals(
      "1 record must be read",
      1,
      recordsReadWithForEach.size()
    );

    AttributeRecord recordRead = recordsReadWithForEach.get(insertedRecord.uniqueId());
    assertNotNull(insertedRecord + " must be read back",
                  recordRead);
    assertArrayEquals(insertedRecord + " must be read back with same content",
                      recordRead.attributeBytes(),
                      insertedRecord.attributeBytes());
  }

  @Test
  public void fewAttributesInsertedForFile_AreAllReportedExistInStorage_AndCouldBeReadBack() throws IOException {
    AttributeRecord[] records = {
      newAttributeRecord(ARBITRARY_FILE_ID, 1)
        .withRandomAttributeBytes(16),
      newAttributeRecord(ARBITRARY_FILE_ID, 5)
        .withRandomAttributeBytes(64),
      newAttributeRecord(ARBITRARY_FILE_ID, 42)
        .withRandomAttributeBytes(128)
    };

    AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    for (AttributeRecord insertedRecord : insertedRecords) {
      assertTrue(
        insertedRecord + ": just inserted -> must exist",
        insertedRecord.existsInStorage(attributesStorage)
      );
      assertArrayEquals(
        insertedRecord + ": content could be read back as-is",
        insertedRecord.attributeBytes(),
        insertedRecord.readValueFromStorage(attributesStorage)
      );
    }
  }

  @Test
  public void fewAttributesInsertedForFile_AreAllReportedExistInStorage_AndCouldBeReadBack_EvenAfterReload() throws IOException {
    int version = 47;
    attributesStorage.setVersion(version);

    AttributeRecord[] records = {
      newAttributeRecord(ARBITRARY_FILE_ID, 1)
        .withRandomAttributeBytes(16),
      newAttributeRecord(ARBITRARY_FILE_ID, 5)
        .withRandomAttributeBytes(64),
      newAttributeRecord(ARBITRARY_FILE_ID, 42)
        .withRandomAttributeBytes(128)
    };

    AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    reopenAttributesStorage();

    assertEquals(
      "Expect to read same version as was written",
      version,
      attributesStorage.getVersion()
    );

    for (AttributeRecord insertedRecord : insertedRecords) {
      assertTrue(
        insertedRecord + ": just inserted -> must exist",
        insertedRecord.existsInStorage(attributesStorage)
      );
      assertArrayEquals(
        insertedRecord + ": content could be read back as-is",
        insertedRecord.attributeBytes(),
        insertedRecord.readValueFromStorage(attributesStorage)
      );
      assertArrayEquals(
        insertedRecord + ": content could be read back as-is (raw)",
        insertedRecord.attributeBytes(),
        insertedRecord.readValueFromStorageRaw(attributesStorage)
      );
    }
  }

  @Test
  public void singleAttributeInsertedAndDeleted_IsNotExistInStorage() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN + 1);

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      insertedRecord.existsInStorage(attributesStorage)
    );

    boolean deleted = attributes.deleteRecord(insertedRecord, attributesStorage);
    assertTrue("Attribute must be deleted successfully",
               deleted);

    boolean exists = insertedRecord.existsInStorage(attributesStorage);
    assertFalse(
      "Attribute just deleted must NOT exist",
      exists
    );
  }

  @Test
  public void singleAttributeInserted_CouldBeDeletedTwice_If_IGNORE_ALREADY_DELETED_ERRORS_Enabled() throws IOException {
    AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN + 1);

    AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue("Attribute just inserted must exist",
               insertedRecord.existsInStorage(attributesStorage)
    );

    boolean deleted = attributes.deleteRecord(insertedRecord, attributesStorage);
    assertTrue("Attribute must be deleted successfully", deleted);
    boolean exists = insertedRecord.existsInStorage(attributesStorage);
    assertFalse("Attribute just deleted must NOT exist", exists);

    if (AttributesStorageOverBlobStorage.IGNORE_ALREADY_DELETED_ERRORS) {
      boolean deletedSecondTime = attributesStorage.deleteAttributes(
        insertedRecord.recordId(),
        insertedRecord.fileId()
      );
      assertFalse("Attribute is already deleted, must not be deleted on second attempt",
                  deletedSecondTime);
    }
    else {
      try {
        attributesStorage.deleteAttributes(
          insertedRecord.recordId(),
          insertedRecord.fileId()
        );
        fail("IGNORE_ALREADY_DELETED_ERRORS=false => must throw error on second attempt to delete already deleted record");
      }
      catch (RecordAlreadyDeletedException e) {
        //OK, it is expected to get an error if IGNORE_ALREADY_DELETED_ERRORS=false
      }
    }
  }

  @Test
  public void manyAttributesInserted_AreAllReportedExistInStorage_AndCouldBeReadBackAsIs() throws IOException {
    int maxAttributeValueSize = Short.MAX_VALUE / 2;
    int differentAttributesCount = 1024;
    Random rnd = ThreadLocalRandom.current();


    AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize, rnd
    );

    AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);


    for (AttributeRecord attributeRecord : insertedRecords) {
      assertTrue(
        attributeRecord + " must exist",
        attributes.existsInStorage(attributeRecord, attributesStorage)
      );
      assertArrayEquals(
        attributeRecord.attributeBytes(),
        attributeRecord.readValueFromStorage(attributesStorage)
      );
      assertArrayEquals(
        attributeRecord.attributeBytes(),
        attributeRecord.readValueFromStorageRaw(attributesStorage)
      );
    }
  }

  @Test
  public void manyAttributesInserted_CouldAllBeReadBackAsIs_WithForEach() throws IOException {
    int maxAttributeValueSize = maxAttributeValueSizeToTest();
    int differentAttributesCount = 1024;
    Random rnd = ThreadLocalRandom.current();

    AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_BIG_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize, rnd
    );

    AttributeRecord[] recordsWritten = attributes.insertOrUpdateAll(records, attributesStorage);

    //RC: there is an issue with current .forEachAttribute() implementation: recordId supplied to callback is not always the same
    // as was used for (returned by) insert/update. This is because for dedicated records recordId reported to callback is an id
    // of _dedicated record_ -- because this is there attribute value is found during file scan-through -- while at insert phase it
    // would be _directory record_ id. This, surely, a surprise from a client PoV.
    // And surely, this could be fixed in the implementation, but with the cost: basically, we'll need to not report dedicated
    // records to the callback immediately, but keep track of them, and report them only after their 'parent' directory records
    // are met, hence 'true' recordId could be determined.
    // This is surely doable, but for now it seems an overkill to do that -- for the practical use cases of .forEachAttribute I
    // have in my mind now fileId and attributeId are important, but 'true' recordId is really not important.
    // Hence, here I decided to use .uniqueId() to match written records with the records read back, and delay more correct implementation
    // until the need for it satisfies its cost.

    Long2ObjectMap<AttributeRecord> recordsReadWithForEach = readAllRecordsWithForEach(attributesStorage);
    assertEquals(
      "Same number of records must be read",
      recordsReadWithForEach.size(),
      recordsWritten.length
    );

    for (AttributeRecord recordWritten : recordsWritten) {
      AttributeRecord recordRead = recordsReadWithForEach.get(recordWritten.uniqueId());
      assertNotNull(recordWritten + " must be read back",
                    recordRead);
      assertArrayEquals(recordWritten + " must be read back with same content",
                        recordRead.attributeBytes(),
                        recordWritten.attributeBytes());
    }
  }

  @Test
  public void manyAttributesInserted_AndDeleted_NotExistAnymore() throws IOException {
    int maxAttributeValueSize = Short.MAX_VALUE / 2;
    int differentAttributesCount = 1024;
    Random rnd = ThreadLocalRandom.current();

    AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize, rnd
    );

    AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    for (AttributeRecord insertedRecord : insertedRecords) {
      assertTrue(
        insertedRecord + " must exist",
        attributes.existsInStorage(insertedRecord, attributesStorage)
      );
    }

    for (AttributeRecord attributeRecord : records) {
      attributes.deleteRecord(attributeRecord, attributesStorage);
    }

    for (AttributeRecord attributeRecord : records) {
      assertFalse(
        attributeRecord + " must NOT exist after being deleted",
        attributes.existsInStorage(attributeRecord, attributesStorage)
      );
    }
  }

  @Test
  public void manyAttributesInserted_AndUpdatedOneByOne_CouldBeReadBackAsIs() throws IOException {
    //Here we check the behaviour of attribute which size crosses INLINE_ATTRIBUTE_MAX_SIZE border up/down
    // -> attribute will change storage format on size change, so let's check this:
    int maxAttributeValueSize = INLINE_ATTRIBUTE_SMALLER_THAN * 3;
    int differentAttributesCount = 1024;
    Random rnd = ThreadLocalRandom.current();

    AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize, rnd
    );

    for (int i = 0; i < records.length; i++) {
      records[i] = attributes.insertOrUpdateRecord(records[i], attributesStorage);
      assertTrue(
        records[i] + " must exist after insert",
        attributes.existsInStorage(records[i], attributesStorage)
      );
    }


    for (int i = 0; i < records.length; i++) {
      AttributeRecord record = records[i];
      int attributeSize = record.attributeBytesLength;
      //grow small attributes, shrink big attributes:
      if (attributeSize <= INLINE_ATTRIBUTE_SMALLER_THAN) {
        record = record.withRandomAttributeBytes(rnd.nextInt(attributeSize, maxAttributeValueSize));
      }
      else {
        record = record.withRandomAttributeBytes(rnd.nextInt(0, attributeSize));
      }
      records[i] = attributes.insertOrUpdateRecord(record, attributesStorage);
    }
    attributes.updateAttributeRecordIds(records);

    for (int i = 0; i < records.length; i++) {
      AttributeRecord record = records[i];
      assertArrayEquals(
        "[" + i + "]" + record + " value must be read",
        record.attributeBytes(),
        record.readValueFromStorage(attributesStorage)
      );

      assertArrayEquals(
        "[" + i + "]" + record + " value must be read (raw)",
        record.attributeBytes(),
        record.readValueFromStorageRaw(attributesStorage)
      );
    }
  }

  @Test
  public void manySmallRecordInserted_AreAllReportedExistInStorage_AndCouldBeReadBack() throws IOException {
    int inlineAttributeSize = INLINE_ATTRIBUTE_SMALLER_THAN - 1;
    int fileId = ARBITRARY_FILE_ID;
    AttributeRecord[] records = IntStream.range(1, 101)
      .mapToObj(attributeId -> newAttributeRecord(fileId, attributeId)
        .withRandomAttributeBytes(inlineAttributeSize))
      .toArray(AttributeRecord[]::new);

    AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    for (AttributeRecord insertedRecord : insertedRecords) {
      assertTrue(
        "Attribute just inserted must exist",
        attributes.existsInStorage(insertedRecord, attributesStorage)
      );
      assertArrayEquals(
        insertedRecord + " value must be read",
        insertedRecord.readValueFromStorage(attributesStorage),
        insertedRecord.attributeBytes()
      );

      assertArrayEquals(
        insertedRecord + " value must be read (raw)",
        insertedRecord.readValueFromStorageRaw(attributesStorage),
        insertedRecord.attributeBytes()
      );
    }
  }


  // ======================== infrastructure: ============================================================== //

  protected abstract AttributesStorageOverBlobStorage openAttributesStorage(@NotNull Path storagePath) throws IOException;

  private static int maxAttributeValueSizeToTest() {
    return Math.min(VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE, PAGE_SIZE - 32);
  }

  protected void reopenAttributesStorage() throws IOException {
    closeStorage();
    attributesStorage = openAttributesStorage(storagePath);
  }

  protected void closeStorage() throws IOException {
    if (attributesStorage != null) {
      attributesStorage.close();
    }
    if (storage != null) {
      storage.close();
    }
  }

  protected static Long2ObjectMap<AttributeRecord> readAllRecordsWithForEach(AttributesStorageOverBlobStorage storage) throws IOException {
    Long2ObjectMap<AttributeRecord> recordsReadWithForEach = new Long2ObjectOpenHashMap<>();

    storage.forEachAttribute((recordId, fileId, attributeId, attributeValue, inlinedAttribute) -> {

      AttributeRecord attributeRecord = new AttributeRecord(recordId, fileId, attributeId)
        .withAttributeBytes(attributeValue, attributeValue.length);

      recordsReadWithForEach.put(attributeRecord.uniqueId(), attributeRecord);
    });

    return recordsReadWithForEach;
  }

  protected static AttributeRecord[] generateManyRandomRecords(int size,
                                                               int differentAttributesCount,
                                                               int maxAttributeValueSize,
                                                               Random rnd) {

    int[] fileIds = rnd.ints()
      .filter(id -> id > 0)
      .limit(size / 2)
      .distinct()
      .toArray();
    int[] attributeIds = rnd.ints(0, VFSAttributesStorage.MAX_ATTRIBUTE_ID + 1)
      .filter(id -> id > 0)
      .limit(differentAttributesCount)
      .distinct()
      .toArray();

    return Stream.generate(() -> {
        int fileId = fileIds[rnd.nextInt(fileIds.length)];
        int attributeId = attributeIds[rnd.nextInt(attributeIds.length)];
        return new IntPair(fileId, attributeId);
      })
      .distinct()//each (fileId,attributeId) pair should occurred only once!
      .limit(size)
      .map(pair -> {
        int fileId = pair.first;
        int attributeId = pair.second;
        return newAttributeRecord(fileId, attributeId)
          .withRandomAttributeBytes(rnd.nextInt(maxAttributeValueSize));
      }).toArray(AttributeRecord[]::new);
  }

  //TODO RC: make AttributeRecord inner class of Attributes, hence methods .store() and .delete()
  //         could be invoked through AttributeRecord itself
  //@Immutable
  public static final class AttributeRecord {
    private final int attributesRecordId;
    private final int fileId;
    private final int attributeId;
    private final byte[] attributeBytes;
    private final int attributeBytesLength;

    public static @NotNull AttributeRecord newAttributeRecord(int fileId,
                                                              int attributeId) {
      return new AttributeRecord(NON_EXISTENT_ATTRIBUTE_RECORD_ID, fileId, attributeId);
    }

    AttributeRecord(int attributesRecordId,
                    int fileId,
                    int attributeId) {
      this(attributesRecordId, fileId, attributeId, new byte[0], 0);
    }

    AttributeRecord(int attributesRecordId,
                    int fileId,
                    int attributeId,
                    byte[] attributeBytes,
                    int attributeBytesLength) {
      this.attributesRecordId = attributesRecordId;
      this.fileId = fileId;
      this.attributeId = attributeId;
      this.attributeBytes = attributeBytes;
      this.attributeBytesLength = attributeBytesLength;
    }

    public long uniqueId() {
      //attributeRecordId is storage-specific, but this id -- which is basically packed(fileId, attributeId) pair -- is
      // really identifies attribute content
      return Integer.toUnsignedLong(fileId) << Integer.SIZE | Integer.toUnsignedLong(attributeId);
    }

    public AttributeRecord withAttributesRecordId(int attributesRecordId) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withFileId(int fileId) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withAttributeId(int attributeId) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withAttributeBytes(byte[] attributeBytes,
                                              int attributeBytesLength) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withRandomAttributeBytes(int size) {
      ThreadLocalRandom rnd = ThreadLocalRandom.current();
      int sizeExcess = rnd.nextInt(size + 1);
      byte[] bytes = generateBytes(rnd, size + sizeExcess);
      return withAttributeBytes(bytes, size);
    }

    public int recordId() {
      return attributesRecordId;
    }

    public int fileId() {
      return fileId;
    }

    public int attributeId() {
      return attributeId;
    }

    public byte[] attributeBytes() {
      return Arrays.copyOf(attributeBytes, attributeBytesLength);
    }

    public int attributeBytesLength() {
      return attributeBytesLength;
    }

    public boolean existsInStorage(AttributesStorageOverBlobStorage attributesStorage) throws IOException {
      return attributesStorage.hasAttribute(
        attributesRecordId,
        fileId,
        attributeId
      );
    }

    public byte[] readValueFromStorage(AttributesStorageOverBlobStorage attributesStorage) throws IOException {
      return attributesStorage.readAttributeValue(attributesRecordId, fileId, attributeId);
    }

    public byte[] readValueFromStorageRaw(AttributesStorageOverBlobStorage attributesStorage) throws IOException {
      return attributesStorage.readAttributeValue(attributesRecordId, fileId, attributeId, buffer -> {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
      });
    }

    @Override
    public String toString() {
      byte[] truncatedValue = Arrays.copyOf(attributeBytes, Math.min(16, attributeBytesLength));
      return "AttributeRecord{" +
             "fileId: " + fileId +
             ", attributeId: " + attributeId +
             ", recordId: " + attributesRecordId +
             "}{bytes: " + Arrays.toString(truncatedValue) + (truncatedValue.length < attributeBytesLength ? "..." : "") +
             '}';
    }
  }

  /**
   * AttributeRecords are logically not independent: in real use-cases attributeRecordId is tiered
   * to fileId (via FSRecords), hence AttributeRecords with the same fileId can't have different attributeRecordIds.
   * This class emulates (very small subset of) FSRecords: it keeps fileId -> attributeRecordId mapping,
   * and maintains it during insertions/updates/deletions -- this is why all modifications should go
   * through it
   */
  public static class Attributes {
    private final Int2IntMap fileIdToAttributeRecordId = new Int2IntOpenHashMap();

    public AttributeRecord insertOrUpdateRecord(AttributeRecord record,
                                                AttributesStorageOverBlobStorage attributesStorage) throws IOException {
      int attributeRecordId = fileIdToAttributeRecordId.get(record.fileId);
      int newAttributeRecordId = attributesStorage.updateAttribute(
        attributeRecordId,
        record.fileId,
        record.attributeId,
        record.attributeBytes,
        record.attributeBytesLength
      );
      if (newAttributeRecordId == NON_EXISTENT_ATTRIBUTE_RECORD_ID) {
        throw new AssertionError("updateAttribute return 0: " + record);
      }
      fileIdToAttributeRecordId.put(record.fileId, newAttributeRecordId);
      return record.withAttributesRecordId(newAttributeRecordId);
    }

    public AttributeRecord[] insertOrUpdateAll(AttributeRecord[] records,
                                               AttributesStorageOverBlobStorage attributesStorage) throws IOException {
      AttributeRecord[] updatedRecords = new AttributeRecord[records.length];
      for (int i = 0; i < records.length; i++) {
        updatedRecords[i] = insertOrUpdateRecord(records[i], attributesStorage);
      }
      updateAttributeRecordIds(updatedRecords);
      return updatedRecords;
    }

    public void updateAttributeRecordIds(AttributeRecord[] updatedRecords) {
      //attribute record id (directory record) could be changed (record relocated) because of later
      // appended attributes. Here we scan all attribute records, and ensure all records with the same
      // fileId have the same attributeRecordId -- most recent one, stored in fileIdToAttributeRecordId
      // mapping
      for (int i = 0; i < updatedRecords.length; i++) {
        AttributeRecord updatedRecord = updatedRecords[i];
        int mostRecentAttributeRecordId = fileIdToAttributeRecordId.get(updatedRecord.fileId);
        if (updatedRecord.attributesRecordId != mostRecentAttributeRecordId) {
          updatedRecords[i] = updatedRecord.withAttributesRecordId(mostRecentAttributeRecordId);
        }
      }
    }

    public boolean deleteRecord(AttributeRecord record,
                                AttributesStorageOverBlobStorage attributesStorage) throws IOException {
      int attributeRecordId = fileIdToAttributeRecordId.getOrDefault(record.fileId, NON_EXISTENT_ATTRIBUTE_RECORD_ID);
      if (attributeRecordId == NON_EXISTENT_ATTRIBUTE_RECORD_ID) {
        return false; //already deleted, do nothing
      }
      boolean deleted = attributesStorage.deleteAttributes(
        attributeRecordId,
        record.fileId
      );
      fileIdToAttributeRecordId.put(record.fileId, NON_EXISTENT_ATTRIBUTE_RECORD_ID);
      return deleted;
    }

    public boolean existsInStorage(AttributeRecord record,
                                   AttributesStorageOverBlobStorage storage) throws IOException {
      int attributeRecordId = fileIdToAttributeRecordId.getOrDefault(record.fileId, NON_EXISTENT_ATTRIBUTE_RECORD_ID);
      if (attributeRecordId == NON_EXISTENT_ATTRIBUTE_RECORD_ID) {
        return false; //already deleted, do nothing
      }
      return storage.hasAttribute(attributeRecordId, record.fileId, record.attributeId);
    }
  }

  protected static byte[] generateBytes(ThreadLocalRandom rnd,
                                        int size) {
    byte[] attributeBytes = new byte[size];
    rnd.nextBytes(attributeBytes);
    return attributeBytes;
  }
}