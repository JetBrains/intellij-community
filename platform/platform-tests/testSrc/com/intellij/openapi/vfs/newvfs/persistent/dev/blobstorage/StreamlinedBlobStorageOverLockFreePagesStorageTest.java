// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage.RecordLayout;
import com.intellij.util.io.PageCacheUtils;
import com.intellij.util.io.PagedFileStorageLockFree;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage.NULL_ID;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage.RecordLayout.ActualRecords.LargeRecord;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage.RecordLayout.ActualRecords.SmallRecord;
import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.StreamlinedBlobStorageOverLockFreePagesStorage.RecordLayout.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class StreamlinedBlobStorageOverLockFreePagesStorageTest
  extends StreamlinedBlobStorageTestBase<StreamlinedBlobStorageOverLockFreePagesStorage> {

  @BeforeClass
  public static void beforeClass() throws Exception {
    assumeTrue(
      "PageCacheUtils.LOCK_FREE_VFS_ENABLED must be true for this test to run",
      PageCacheUtils.LOCK_FREE_VFS_ENABLED
    );
  }

  public StreamlinedBlobStorageOverLockFreePagesStorageTest(final @NotNull Integer pageSize,
                                                            final @NotNull SpaceAllocationStrategy strategy) {
    super(pageSize, strategy);
  }


  @Override
  protected StreamlinedBlobStorageOverLockFreePagesStorage openStorage(final Path pathToStorage) throws IOException {
    final PagedFileStorageLockFree pagedStorage = new PagedFileStorageLockFree(
      pathToStorage,
      LOCK_CONTEXT,
      pageSize,
      true
    );
    return new StreamlinedBlobStorageOverLockFreePagesStorage(
      pagedStorage,
      allocationStrategy
    );
  }

  @Override
  public void tearDown() throws Exception {
    if (storage != null) {
      System.out.printf("Storage after test: %d records allocated, %d deleted, %d relocated, live records %.1f%% of total \n",
                        storage.recordsAllocated(),
                        storage.recordsDeleted(),
                        storage.recordsRelocated(),
                        storage.liveRecordsCount() * 100.0 / storage.recordsAllocated()
      );
      System.out.printf("                    %d bytes live payload, %d live capacity, live payload %.1f%% of total storage size \n",
                        storage.totalLiveRecordsPayloadBytes(),
                        storage.totalLiveRecordsCapacityBytes(),
                        storage.totalLiveRecordsPayloadBytes() * 100.0 / storage.sizeInBytes() //including _storage_ header
      );
    }
    super.tearDown();
  }

  public static class RecordsLayoutTest {
    @Test
    public void smallRecordHeaderFields_RestoredFromBufferAsIs() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(SmallRecord.MAX_CAPACITY + OFFSET_BUCKET);
      final ByteBuffer payload = ByteBuffer.allocate(SmallRecord.MAX_CAPACITY + OFFSET_BUCKET);

      final SmallRecord recordLayout = SmallRecord.INSTANCE;

      for (int capacity = SmallRecord.MIN_CAPACITY; capacity <= SmallRecord.MAX_CAPACITY; capacity += OFFSET_BUCKET) {
        for (int length = 0; length <= capacity; length++) {
          payload.position(0).limit(length);

          recordLayout.putRecord(buffer, 0, capacity, length, NULL_ID, payload);

          final RecordLayout readLayout = recordLayout(buffer, 0);

          assertEquals(
            "[" + capacity + ", " + length + "]: Record type must be set to ACTUAL",
            RECORD_TYPE_ACTUAL,
            readLayout.recordType()
          );
          assertEquals(
            "[" + capacity + ", " + length + "]: Saved capacity must be restored as-is",
            capacity,
            readLayout.capacity(buffer, 0)
          );
          assertEquals(
            "[" + capacity + ", " + length + "]: Saved length must be restored as-is",
            length,
            readLayout.length(buffer, 0)
          );
        }
      }
    }

    @Test
    public void largeRecordHeaderFields_RestoredFromBufferAsIs() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(LargeRecord.MAX_CAPACITY + OFFSET_BUCKET);
      final ByteBuffer payload = ByteBuffer.allocate(LargeRecord.MAX_CAPACITY + OFFSET_BUCKET);

      final LargeRecord recordLayout = LargeRecord.INSTANCE;
      for (int capacity = LargeRecord.MIN_CAPACITY;
           capacity <= LargeRecord.MAX_CAPACITY;
           capacity += OFFSET_BUCKET) {

        //too expensive to check all length in [0..capacity], so just check a few:
        final int[] lengthsToCheck = IntStream.of(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
            capacity / 2 + 1,
            capacity - 3, capacity - 2, capacity - 1, capacity
          )
          .filter(length -> length >= 0 && length <= LargeRecord.MAX_CAPACITY)
          .toArray();
        for (int length : lengthsToCheck) {
          payload.position(0).limit(length);

          recordLayout.putRecord(buffer, 0, capacity, length, NULL_ID, payload);

          final RecordLayout readLayout = recordLayout(buffer, 0);

          assertEquals(
            "[" + capacity + ", " + length + "]: Record type must be set to ACTUAL",
            RECORD_TYPE_ACTUAL,
            readLayout.recordType()
          );
          assertEquals(
            "[" + capacity + ", " + length + "]: Saved capacity must be restored as-is",
            capacity,
            readLayout.capacity(buffer, 0)
          );
          assertEquals(
            "[" + capacity + ", " + length + "]: Saved length must be restored as-is",
            length,
            readLayout.length(buffer, 0)
          );
        }
      }
    }

    @Test
    public void movedRecordHeaderFields_RestoredFromBufferAsIs() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(MovedRecord.MAX_CAPACITY + OFFSET_BUCKET);
      final ByteBuffer payload = ByteBuffer.allocate(MovedRecord.MAX_CAPACITY + OFFSET_BUCKET);
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final int[] redirectToIds = rnd.ints(16).toArray();

      final MovedRecord recordLayout = MovedRecord.INSTANCE;
      for (int capacity = MovedRecord.MIN_CAPACITY;
           capacity <= MovedRecord.MAX_CAPACITY;
           capacity += OFFSET_BUCKET) {
        for (final int redirectToId : redirectToIds) {
          payload.position(0).limit(capacity);

          recordLayout.putRecord(buffer, 0, capacity, 0, redirectToId, payload);
          final RecordLayout readLayout = recordLayout(buffer, 0);

          assertEquals(
            "[" + capacity + ", " + redirectToId + "]: Record type must be set to MOVED",
            RECORD_TYPE_MOVED,
            readLayout.recordType()
          );
          assertEquals(
            "[" + capacity + ", " + redirectToId + "]: Saved capacity must be restored as-is",
            capacity,
            readLayout.capacity(buffer, 0)
          );
          assertEquals(
            "[" + capacity + ", " + redirectToId + "]: length of MOVED record must be 0",
            0,
            readLayout.length(buffer, 0)
          );
          assertEquals(
            "[" + capacity + ", " + redirectToId + "]: Saved redirectToId must be restored as-is",
            redirectToId,
            readLayout.redirectToId(buffer, 0)
          );
        }
      }
    }

    @Test
    public void paddingRecordHeaderFields_RestoredFromBufferAsIs() throws Exception {
      final ByteBuffer buffer = ByteBuffer.allocate(MovedRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());
      final ByteBuffer payload = ByteBuffer.allocate(MovedRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());

      final PaddingRecord recordLayout = PaddingRecord.INSTANCE;
      for (int capacity = PaddingRecord.MIN_CAPACITY; capacity <= PaddingRecord.MAX_CAPACITY; capacity += OFFSET_BUCKET) {
        payload.position(0).limit(capacity);

        recordLayout.putRecord(buffer, 0, capacity, 0, NULL_ID, payload);
        final RecordLayout readLayout = recordLayout(buffer, 0);

        assertEquals(
          "[" + capacity + "]: Record type must be set to PADDING",
          RECORD_TYPE_PADDING,
          readLayout.recordType()
        );
        assertEquals(
          "[" + capacity + "]: Saved capacity must be restored as-is",
          capacity,
          readLayout.capacity(buffer, 0)
        );
        assertEquals(
          "[" + capacity + "]: length of PADDING record must be 0",
          0,
          readLayout.length(buffer, 0)
        );
      }
    }
  }
}