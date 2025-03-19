// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.blobstorage;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static com.intellij.platform.util.io.storages.blobstorage.RecordLayout.*;
import static com.intellij.util.io.blobstorage.StreamlinedBlobStorage.NULL_ID;
import static org.junit.Assert.assertEquals;

public class RecordLayoutTest {
  @Test
  public void smallRecordHeaderFields_RestoredFromBufferAsIs() throws Exception {
    final ByteBuffer buffer = ByteBuffer.allocate(ActualRecords.SmallRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());
    final ByteBuffer payload = ByteBuffer.allocate(ActualRecords.SmallRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());

    final ActualRecords.SmallRecord recordLayout = ActualRecords.SmallRecord.INSTANCE;

    for (int capacity = ActualRecords.SmallRecord.MIN_CAPACITY;
         capacity <= ActualRecords.SmallRecord.MAX_CAPACITY;
         capacity += OFFSET_BUCKET) {
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
    final ByteBuffer buffer = ByteBuffer.allocate(ActualRecords.LargeRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());
    final ByteBuffer payload = ByteBuffer.allocate(ActualRecords.LargeRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());

    final ActualRecords.LargeRecord recordLayout = ActualRecords.LargeRecord.INSTANCE;
    for (int capacity = ActualRecords.LargeRecord.MIN_CAPACITY;
         capacity <= ActualRecords.LargeRecord.MAX_CAPACITY;
         capacity += OFFSET_BUCKET) {

      //too expensive to check all length in [0..capacity], so just check a few:
      final int[] lengthsToCheck = IntStream.of(
          0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
          capacity / 2 + 1,
          capacity - 3, capacity - 2, capacity - 1, capacity
        )
        .filter(length -> length >= 0 && length <= ActualRecords.LargeRecord.MAX_CAPACITY)
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
    final ByteBuffer buffer = ByteBuffer.allocate(MovedRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());
    final ByteBuffer payload = ByteBuffer.allocate(MovedRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());
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
    final ByteBuffer buffer = ByteBuffer.allocate(PaddingRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());
    final ByteBuffer payload = ByteBuffer.allocate(PaddingRecord.MAX_CAPACITY + OFFSET_BUCKET).order(ByteOrder.nativeOrder());

    final PaddingRecord recordLayout = PaddingRecord.INSTANCE;
    for (int capacity = PaddingRecord.MIN_CAPACITY;
         capacity <= PaddingRecord.MAX_CAPACITY;
         capacity += OFFSET_BUCKET) {
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
