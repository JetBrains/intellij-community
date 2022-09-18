// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage;
import com.intellij.openapi.vfs.newvfs.persistent.dev.StreamlinedBlobStorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.util.IntPair;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.PagedFileStorage;
import com.intellij.util.io.StorageLockContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
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

  /** Not so much records because each of them could be up to 64k, which leads to OoM quite quickly */
  private static final int ENOUGH_RECORDS = 1 << 15;

  private static final int ARBITRARY_FILE_ID = 157;
  private static final int ARBITRARY_ATTRIBUTE_ID = 10;


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
    final int attributeId = ARBITRARY_ATTRIBUTE_ID;
    final AttributeRecord record = new AttributeRecord(nonInsertedRecordId, ARBITRARY_FILE_ID, attributeId)
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

    final AttributeRecord insertedRecord = record.store(attributesStorage);

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

    final AttributeRecord insertedRecord = record.store(attributesStorage);

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

    for (int i = 0; i < records.length; i++) {
      records[i] = records[i].store(attributesStorage);
    }

    for (AttributeRecord insertedRecord : records) {
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

    for (int i = 0; i < records.length; i++) {
      records[i] = records[i].store(attributesStorage);
    }

    closeStorage();
    openStorage();

    assertEquals(
      "Expect to read same version as was written",
      attributesStorage.getVersion(),
      version
    );

    for (AttributeRecord insertedRecord : records) {
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

    final AttributeRecord insertedRecord = record.store(attributesStorage);

    assertTrue(
      "Attribute just inserted must exist",
      insertedRecord.existsInStorage(attributesStorage)
    );

    final boolean deleted = insertedRecord.delete(attributesStorage);
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
    for (int i = 0; i < records.length; i++) {
      records[i] = records[i].store(attributesStorage);
    }

    for (final AttributeRecord attributeRecord : records) {
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
    final AttributeRecord[] recordsWritten = generateManyRandomRecords(
      ENOUGH_RECORDS,
      differentAttributesCount,
      maxAttributeValueSize
    );
    for (int i = 0; i < recordsWritten.length; i++) {
      recordsWritten[i] = recordsWritten[i].store(attributesStorage);
    }

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
    for (int i = 0; i < records.length; i++) {
      records[i] = records[i].store(attributesStorage);
      assertTrue(
        records[i] + " must exist",
        records[i].existsInStorage(attributesStorage)
      );
    }

    for (final AttributeRecord attributeRecord : records) {
      attributeRecord.delete(attributesStorage);
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
      records[i] = records[i].store(attributesStorage);
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
      records[i] = record.store(attributesStorage);
    }

    for (AttributeRecord record : records) {
      assertArrayEquals(
        record + " value must be read",
        record.readValueFromStorage(attributesStorage),
        record.attributeBytes()
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

    public AttributeRecord store(final AttributesStorageOnTheTopOfBlobStorage storage) throws IOException {
      final int updatedRecordId = storage.updateAttribute(
        attributesRecordId,
        fileId,
        attributeId,
        attributeBytes,
        attributeBytesLength
      );
      return withAttributesRecordId(updatedRecordId);
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

    public boolean delete(final AttributesStorageOnTheTopOfBlobStorage storage) throws IOException {
      return storage.deleteAttributes(attributesRecordId, fileId);
    }
  }

  @NotNull
  private static byte[] generateBytes(final ThreadLocalRandom rnd,
                                      final int size) {
    final byte[] attributeBytes = new byte[size];
    rnd.nextBytes(attributeBytes);
    return attributeBytes;
  }
}