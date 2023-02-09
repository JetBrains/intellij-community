// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage.AttributeEntry;
import com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOverBlobStorage.AttributesRecord;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.SpaceAllocationStrategy.DataLengthPlusFixedPercentStrategy;
import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageLockFree;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import static com.intellij.openapi.vfs.newvfs.persistent.AttributesStorageOnTheTopOfBlobStorageTestBase.AttributeRecord.newAttributeRecord;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 *
 */
public class AttributesStorageOnTheTopOfStreamlinedBlobStorageLockFreeTest
  extends AttributesStorageOnTheTopOfBlobStorageTestBase {

  @BeforeClass
  public static void checkLockFreePageCacheIsEnabled() throws Exception {
    assumeTrue(
      "PageCacheUtils.LOCK_FREE_VFS_ENABLED=false: the FilePageCacheLockFree must be enabled for this test",
      PageCacheUtils.LOCK_FREE_VFS_ENABLED
    );
  }

  @Override
  protected AttributesStorageOverBlobStorage openAttributesStorage(final Path storagePath) throws IOException {
    final PagedFileStorageLockFree pagedStorage = new PagedFileStorageLockFree(
      this.storagePath,
      LOCK_CONTEXT,
      PAGE_SIZE,
      true
    );
    storage = new StreamlinedBlobStorageOverLockFreePagesStorage(
      pagedStorage,
      new DataLengthPlusFixedPercentStrategy(256, 64, 30)
    );
    return new AttributesStorageOverBlobStorage(storage);
  }

  @Test
  public void recordWithZeroPayload_CouldBeInserted_AndReadBackAsIs() throws IOException {
    final AttributeRecord recordWithZeroPayload = attributes.insertOrUpdateRecord(
      newAttributeRecord(ARBITRARY_FILE_ID, ARBITRARY_ATTRIBUTE_ID)
        .withAttributeBytes(new byte[0], 0),
      attributesStorage
    );

    assertTrue(
      "Inserted record with 0 payload is exist",
      recordWithZeroPayload.existsInStorage(attributesStorage)
    );
    final byte[] readBack = recordWithZeroPayload.readValueFromStorage(attributesStorage);
    assertEquals(
      "Payload is still 0",
      0,
      readBack.length
    );
  }

  /**
   * Test for specific implementation methods: writing/reading attribute record/entries from a
   * buffer
   */
  public static class InternalsTest {

    @Test
    public void emptyBufferHasNoRecords() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);
      buffer.limit(0);
      final AttributesRecord record = new AttributesRecord(buffer);
      assertFalse("Buffer is empty -> must contain no record",
                  record.hasDirectory());
    }

    @Test
    public void directoryRecord_WrittenIntoBuffer_CouldBeReadBack() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);
      final int fileId = 1205454;
      AttributesRecord.putDirectoryRecordHeader(buffer, fileId);
      buffer.flip();
      final AttributesRecord record = new AttributesRecord(buffer);
      assertTrue("Buffer must contain 1 record",
                 record.hasDirectory());
      assertEquals(
        "Written fileId must be read back",
        fileId,
        record.fileId()
      );

      assertFalse(
        "Must be no attribute entries",
        record.currentEntry().isValid()
      );
    }

    @Test
    public void inlineEntry_WrittenIntoBuffer_CouldBeReadBack() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);

      final int fileId = 1205454;
      for (int attributeId = 1; attributeId <= Byte.MAX_VALUE; attributeId++) {
        for (int size = 1; size < AbstractAttributesStorage.INLINE_ATTRIBUTE_SMALLER_THAN; size++) {
          buffer.clear();

          AttributesRecord.putDirectoryRecordHeader(buffer, fileId);
          AttributeEntry.putInlineEntry(buffer, attributeId, new byte[size], size);

          buffer.flip();

          final AttributesRecord record = new AttributesRecord(buffer);
          assertTrue("Buffer must contain 1 record",
                     record.hasDirectory());

          final AttributeEntry entry = record.currentEntry();
          assertTrue(
            "Must be 1 attribute entry",
            entry.isValid()
          );
          assertEquals(
            "Entry must have attributeId written before",
            attributeId,
            entry.attributeId()
          );
          assertEquals(
            "Entry must have size written before",
            size,
            entry.inlinedValueLength()
          );
        }
      }
    }

    @Test
    public void refEntry_WrittenIntoBuffer_CouldBeReadBack() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(1024);

      final int fileId = 1205454;
      final int[] refIds = ThreadLocalRandom.current().ints(1).limit(100_000_000).toArray();
      for (int attributeId = 1; attributeId <= Byte.MAX_VALUE; attributeId++) {
        for (final int dedicatedRefId : refIds) {
          buffer.clear();

          AttributesRecord.putDirectoryRecordHeader(buffer, fileId);

          AttributeEntry.putRefEntry(buffer, attributeId, dedicatedRefId);

          buffer.flip();

          final AttributesRecord record = new AttributesRecord(buffer);
          assertTrue("Buffer must contain 1 record",
                     record.hasDirectory());

          final AttributeEntry entry = record.currentEntry();
          assertTrue(
            "Must be 1 attribute entry",
            entry.isValid()
          );
          assertEquals(
            "Entry must have attributeId written before",
            attributeId,
            entry.attributeId()
          );
          assertEquals(
            "Entry must have refId written before",
            dedicatedRefId,
            entry.dedicatedValueRecordId()
          );
        }
      }
    }

    @Test
    public void resizeGap_keepsBeforeAndAfterSlicesIntact() {
      final ByteBuffer buffer = ByteBuffer.allocate(200);

      final int limitInitial = 128;
      final byte[] bytes = generateBytes(ThreadLocalRandom.current(), buffer.capacity());

      for (int offset = 0; offset < limitInitial; offset++) {
        for (int oldGap = 0; oldGap < limitInitial - offset; oldGap++) {
          for (int newGap = 0; newGap < 128; newGap++) {
            final int positionBefore = offset;
            buffer
              .clear()
              .put(bytes)
              .position(positionBefore).limit(limitInitial);

            final ByteBuffer newBuffer = AttributesStorageOverBlobStorage.resizeGap(buffer, offset, oldGap, newGap);

            assertEquals(
              "Position must be unchanged",
              positionBefore,
              newBuffer.position()
            );
            assertEquals(
              "Limit must adjust for the gaps difference (" + (newGap - oldGap) + ")",
              limitInitial + (newGap - oldGap),
              newBuffer.limit()
            );

            final byte[] firstSlice = new byte[offset];
            newBuffer.get(0, firstSlice);
            assertArrayEquals(
              "Bytes before the gap [0.." + offset + ") must remain unchanged",
              firstSlice,
              Arrays.copyOf(bytes, offset)
            );

            final byte[] lastSlice = new byte[limitInitial - offset - oldGap];
            newBuffer.get(offset + newGap, lastSlice);
            assertArrayEquals(
              "Bytes after the gap (" + (offset + newGap) + ".." + limitInitial + ") must remain unchanged",
              lastSlice,
              Arrays.copyOfRange(bytes, offset + oldGap, limitInitial)
            );
          }
        }
      }
    }
  }
}
