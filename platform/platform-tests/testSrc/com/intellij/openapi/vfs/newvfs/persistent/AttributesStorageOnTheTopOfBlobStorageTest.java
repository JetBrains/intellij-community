// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.IntPair;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.INLINE_ATTRIBUTE_SMALLER_THAN;
import static com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTest.AttributeRecord.*;
import static com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage.NON_EXISTENT_ATTR_RECORD_ID;
import static org.junit.Assert.*;

/**
 *
 */
public class AttributesStorageOnTheTopOfBlobStorageTest {

  private static final int PAGE_SIZE = 1 << 15;
  private static final StorageLockContext LOCK_CONTEXT = new StorageLockContext(true, true);

  /**
   * Not so much records because each of them could be up to 64k, which leads to OoM quite quickly
   */
  private static final int ENOUGH_RECORDS = 1 << 15;

  private static final int ARBITRARY_FILE_ID = 157;
  private static final int ARBITRARY_ATTRIBUTE_ID = 10;
  private Attributes attributes;


  @BeforeClass
  public static void beforeClass() throws Exception {
    IndexDebugProperties.DEBUG = true;
  }

  @Rule
  public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Path storagePath;
  private StreamlinedBlobStorage storage;

  private AttributesStorageOnTheTopOfBlobStorage attributesStorage;

  @Before
  public void setUp() throws Exception {
    attributes = new Attributes();

    storagePath = temporaryFolder.newFile().toPath();
    openStorage();
  }

  @After
  public void tearDown() throws Exception {
    closeStorage();
  }

  @Test
  public void nonInsertedRecordIsNotExistsInStorage() throws IOException {
    final int nonInsertedRecordId = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
    final AttributeRecord record = new AttributeRecord(nonInsertedRecordId, ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(16);
    assertFalse(
      "Not inserted record is not exists",
      record.existsInStorage(attributesStorage)
    );
  }

  @Test
  public void singleSmallRecordInserted_ExistsInStorage_AndCouldBeReadBack() throws IOException {
    final AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN - 1);

    final AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

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
  public void singleBigRecordInserted_ExistsInStorage_AndCouldBeReadBack() throws IOException {
    final AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN + 1);

    final AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

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
  public void fewAttributesInsertedForFile_ExistsInStorage_AndCouldBeReadBack() throws IOException {
    final AttributeRecord[] records = {
      newAttributeRecord(ARBITRARY_FILE_ID, 1)
        .withRandomAttributeBytes(16),
      newAttributeRecord(ARBITRARY_FILE_ID, 5)
        .withRandomAttributeBytes(64),
      newAttributeRecord(ARBITRARY_FILE_ID, 42)
        .withRandomAttributeBytes(128)
    };

    final AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

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
  public void fewAttributesInsertedForFile_ExistsInStorage_AndCouldBeReadBack_EvenAfterReload() throws IOException {
    final int version = 47;
    attributesStorage.setVersion(version);

    final AttributeRecord[] records = {
      newAttributeRecord(ARBITRARY_FILE_ID, 1)
        .withRandomAttributeBytes(16),
      newAttributeRecord(ARBITRARY_FILE_ID, 5)
        .withRandomAttributeBytes(64),
      newAttributeRecord(ARBITRARY_FILE_ID, 42)
        .withRandomAttributeBytes(128)
    };

    final AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    closeStorage();
    openStorage();

    assertEquals(
      "Expect to read same version as was written",
      attributesStorage.getVersion(),
      version
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
    }
  }

  @Test
  public void singleAttributeInsertedAndDeleted_IsNotExistInStorage() throws IOException {
    final AttributeRecord record = newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
      .withRandomAttributeBytes(INLINE_ATTRIBUTE_SMALLER_THAN + 1);

    final AttributeRecord insertedRecord = attributes.insertOrUpdateRecord(record, attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      insertedRecord.existsInStorage(attributesStorage)
    );

    final boolean deleted = attributes.deleteRecord(insertedRecord, attributesStorage);
    assertTrue("Attribute must be deleted successfully",
               deleted);

    final boolean exists = insertedRecord.existsInStorage(attributesStorage);
    assertFalse(
      "Attribute just deleted must NOT exist",
      exists
    );
  }

  @Test
  public void manyAttributesInserted_Exists_AndCouldBeReadBackAsIs() throws IOException {
    final int maxAttributeValueSize = Short.MAX_VALUE / 2;
    final int differentAttributesCount = 1024;
    final AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize
    );

    final AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);


    for (final AttributeRecord attributeRecord : insertedRecords) {
      assertTrue(
        attributeRecord + " must exist",
        attributeRecord.existsInStorage(attributesStorage)
      );
      assertArrayEquals(
        attributeRecord.attributeBytes(),
        attributeRecord.readValueFromStorage(attributesStorage)
      );
    }
  }

  @Test
  public void manyAttributesInserted_Exists_AndCouldBeReadBackAsIs_WithForEach() throws IOException {
    final int maxAttributeValueSize = Short.MAX_VALUE / 2;
    final int differentAttributesCount = 1024;
    final AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize
    );

    final AttributeRecord[] recordsWritten = attributes.insertOrUpdateAll(records, attributesStorage);

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

    final Long2ObjectMap<AttributeRecord> recordsReadWithForEach = new Long2ObjectOpenHashMap<>();
    attributesStorage.forEachAttribute((recordId, fileId, attributeId, attributeValue) -> {
      final AttributeRecord attributeRecord = new AttributeRecord(recordId, fileId, attributeId)
        .withAttributeBytes(attributeValue, attributeValue.length);
      recordsReadWithForEach.put(
        attributeRecord.uniqueId(),
        attributeRecord
      );
    });
    assertEquals(
      "Same number of records must be read",
      recordsReadWithForEach.size(),
      recordsWritten.length
    );

    for (AttributeRecord recordWritten : recordsWritten) {
      final AttributeRecord recordRead = recordsReadWithForEach.get(recordWritten.uniqueId());
      assertNotNull(recordWritten + " must be read back",
                    recordRead);
      assertArrayEquals(recordWritten + " must be read back with same content",
                        recordRead.attributeBytes(),
                        recordWritten.attributeBytes());
    }
  }

  @Test
  public void manyAttributesInserted_AndDeleted_NotExistAnymore() throws IOException {
    final int maxAttributeValueSize = Short.MAX_VALUE / 2;
    final int differentAttributesCount = 1024;
    final AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize
    );

    final AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    for (AttributeRecord insertedRecord : insertedRecords) {
      assertTrue(
        insertedRecord + " must exist",
        insertedRecord.existsInStorage(attributesStorage)
      );
    }

    for (final AttributeRecord attributeRecord : records) {
      attributes.deleteRecord(attributeRecord, attributesStorage);
    }

    for (AttributeRecord attributeRecord : records) {
      assertFalse(
        attributeRecord + " must NOT exist after being deleted",
        attributeRecord.existsInStorage(attributesStorage)
      );
    }
  }

  @Test
  public void manyAttributesInserted_AndUpdatedOneByOne_CouldBeReadBackAsIs() throws IOException {
    //Here we check behaviour of attribute which size crosses INLINE_ATTRIBUTE_MAX_SIZE border up/down
    // -> attribute will change storage format on size change, so lets check this:
    final int maxAttributeValueSize = INLINE_ATTRIBUTE_SMALLER_THAN * 3;
    final int differentAttributesCount = 1024;
    final AttributeRecord[] records = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize
    );

    for (int i = 0; i < records.length; i++) {
      records[i] = attributes.insertOrUpdateRecord(records[i], attributesStorage);
      assertTrue(
        records[i] + " must exist after insert",
        records[i].existsInStorage(attributesStorage)
      );
    }

    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < records.length; i++) {
      AttributeRecord record = records[i];
      final int attributeSize = record.attributeBytesLength;
      //grow small attributes, shrink big attributes:
      if (attributeSize <= INLINE_ATTRIBUTE_SMALLER_THAN) {
        record = record.withRandomAttributeBytes(rnd.nextInt(attributeSize, maxAttributeValueSize));
      }
      else {
        record = record.withRandomAttributeBytes(rnd.nextInt(0, attributeSize));
      }
      records[i] = attributes.insertOrUpdateRecord(record, attributesStorage);
    }

    for (AttributeRecord record : records) {
      assertArrayEquals(
        record + " value must be read",
        record.readValueFromStorage(attributesStorage),
        record.attributeBytes()
      );
    }
  }

  @Test
  public void manySmallRecordInserted_ExistsInStorage_AndCouldBeReadBack() throws IOException {
    final int inlineAttributeSize = INLINE_ATTRIBUTE_SMALLER_THAN - 1;
    final int fileId = ARBITRARY_FILE_ID;
    final AttributeRecord[] records = IntStream.range(0, 100)
      .mapToObj(attributeId -> newAttributeRecord(fileId, attributeId)
        .withRandomAttributeBytes(inlineAttributeSize))
      .toArray(AttributeRecord[]::new);

    final AttributeRecord[] insertedRecords = attributes.insertOrUpdateAll(records, attributesStorage);

    for (final AttributeRecord insertedRecord : insertedRecords) {
      assertTrue(
        "Attribute just inserted must exist",
        insertedRecord.existsInStorage(attributesStorage)
      );
      assertArrayEquals(
        insertedRecord + " value must be read",
        insertedRecord.readValueFromStorage(attributesStorage),
        insertedRecord.attributeBytes()
      );
    }
  }


  /* ======================== infrastructure ============================================================== */

  private void openStorage() throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(
      storagePath,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true,
      true
    );
    storage = new StreamlinedBlobStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(256, 64, 30)
    );
    attributesStorage = new AttributesStorageOnTheTopOfBlobStorage(storage);
  }

  private void closeStorage() throws IOException {
    if (attributesStorage != null) {
      attributesStorage.close();
    }
    if (storage != null) {
      storage.close();
    }
  }

  private static AttributeRecord[] generateManyRandomRecords(final int size,
                                                             final int differentAttributesCount,
                                                             final int maxAttributeValueSize) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();

    final int[] fileIds = rnd.ints()
      .filter(id -> id > 0)
      .limit(size / 2)
      .toArray();
    final int[] attributeIds = rnd.ints()
      .filter(id -> id > 0)
      .limit(differentAttributesCount)
      .toArray();

    return Stream.generate(() -> {
        final int fileId = fileIds[rnd.nextInt(fileIds.length)];
        final int attributeId = attributeIds[rnd.nextInt(attributeIds.length)];
        return new IntPair(fileId, attributeId);
      })
      .distinct()//each (fileId,attributeId) pair should occured only once!
      .limit(size)
      .map(pair -> {
        final int fileId = pair.first;
        final int attributeId = pair.second;
        return newAttributeRecord(fileId, attributeId)
          .withRandomAttributeBytes(rnd.nextInt(maxAttributeValueSize));
      }).toArray(AttributeRecord[]::new);
  }

  //TODO RC: make AttributeRecord inner class of Attributes, hence methods .store() and .delete()
  //         could be invoked through AttributeRecord itself
  //@Immutable
  public static class AttributeRecord {
    private final int attributesRecordId;
    private final int fileId;
    private final int attributeId;
    private final byte[] attributeBytes;
    private final int attributeBytesLength;

    @NotNull
    public static AttributeRecord newAttributeRecord(final int fileId,
                                                     final int attributeId) {
      return new AttributeRecord(NON_EXISTENT_ATTR_RECORD_ID, fileId, attributeId);
    }

    protected AttributeRecord(final int attributesRecordId,
                              final int fileId,
                              final int attributeId) {
      this(attributesRecordId, fileId, attributeId, new byte[0], 0);
    }

    protected AttributeRecord(final int attributesRecordId,
                              final int fileId,
                              final int attributeId,
                              final byte[] attributeBytes,
                              final int attributeBytesLength) {
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

    public AttributeRecord withAttributesRecordId(final int attributesRecordId) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withFileId(final int fileId) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withAttributeId(final int attributeId) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withAttributeBytes(final byte[] attributeBytes,
                                              final int attributeBytesLength) {
      return new AttributeRecord(attributesRecordId, fileId, attributeId, attributeBytes, attributeBytesLength);
    }

    public AttributeRecord withRandomAttributeBytes(final int size) {
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final int sizeExcess = rnd.nextInt(size + 1);
      final byte[] bytes = generateBytes(rnd, size + sizeExcess);
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

    public boolean existsInStorage(final AttributesStorageOnTheTopOfBlobStorage attributesStorage) throws IOException {
      return attributesStorage.hasAttribute(
        attributesRecordId,
        fileId,
        attributeId
      );
    }

    public byte[] readValueFromStorage(final AttributesStorageOnTheTopOfBlobStorage attributesStorage) throws IOException {
      return attributesStorage.readAttributeValue(attributesRecordId, fileId, attributeId);
    }

    @Override
    public String toString() {
      final byte[] truncatedValue = Arrays.copyOf(attributeBytes, Math.min(16, attributeBytesLength));
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
   * to fileId (via FSRecords), hence AttributeRecords with same fileId can't have different attributeRecordIds.
   * This class emulates (very small subset of) FSRecords: it keeps fileId -> attributeRecordId mapping,
   * and maintains it during insertions/updates/deletions -- this is why all modifications should go
   * through it
   */
  public static class Attributes {
    private final Int2IntMap fileIdToAttributeRecordId = new Int2IntOpenHashMap();

    public AttributeRecord insertOrUpdateRecord(final AttributeRecord record,
                                                final AttributesStorageOnTheTopOfBlobStorage attributesStorage) throws IOException {
      final int attributeRecordId = fileIdToAttributeRecordId.get(record.fileId);
      final int newAttributeRecordId = attributesStorage.updateAttribute(
        attributeRecordId,
        record.fileId,
        record.attributeId,
        record.attributeBytes,
        record.attributeBytesLength
      );
      fileIdToAttributeRecordId.put(record.fileId, newAttributeRecordId);
      return record.withAttributesRecordId(newAttributeRecordId);
    }

    public AttributeRecord[] insertOrUpdateAll(final AttributeRecord[] records,
                                               final AttributesStorageOnTheTopOfBlobStorage attributesStorage) throws IOException {
      final AttributeRecord[] updatedRecords = new AttributeRecord[records.length];
      for (int i = 0; i < records.length; i++) {
        updatedRecords[i] = insertOrUpdateRecord(records[i], attributesStorage);
      }
      return updatedRecords;
    }

    public boolean deleteRecord(final AttributeRecord record,
                                final AttributesStorageOnTheTopOfBlobStorage attributesStorage) throws IOException {
      final int attributeRecordId = fileIdToAttributeRecordId.getOrDefault(record.fileId, NON_EXISTENT_ATTR_RECORD_ID);
      if (attributeRecordId == NON_EXISTENT_ATTR_RECORD_ID) {
        return false; //already deleted, do nothing
      }
      final boolean deleted = attributesStorage.deleteAttributes(
        attributeRecordId,
        record.fileId
      );
      fileIdToAttributeRecordId.put(record.fileId, NON_EXISTENT_ATTR_RECORD_ID);
      return deleted;
    }
  }

  private static byte[] generateBytes(final ThreadLocalRandom rnd,
                                      final int size) {
    final byte[] attributeBytes = new byte[size];
    rnd.nextBytes(attributeBytes);
    return attributeBytes;
  }
}