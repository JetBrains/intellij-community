// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

import com.intellij.util.io.blobstorage.StreamlinedBlobStorage;

import java.nio.ByteBuffer;

import static com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage.RecordLayout.ActualRecords.recordLayoutForType;

/**
 * RECORD format:
 * <p>
 * We try to compress headers as much as possible, to add the lowest overhead to record payload, especially
 * when the payload is small. For that we use multiple record types, each of types defines its own set of
 * fields -- so we do not waste space on fields not actual for a given record type. Record type is defined
 * by the first 2 bits of the first header byte, and the 6 bits remaining are used to store type-specific
 * data. To compress data even further, we also use the fact that total record size is always a multiple 8
 * (see OFFSET_BUCKET) -- thus we could store capacity in 3 bits less.
 * <p>
 * <b>BEWARE</b>: header[a..b] notation below means _bits_ 'a' to 'b' -- not bytes as it is usually written!
 * <pre>
 * Record format:
 *
 *  record: header[1b+] (type, capacity[?]?, length[?]?, redirectTo[?]?, data[?]?)
 *  type: header[0..1] = [ACTUAL|MOVED|PADDING|PARTIAL]
 *  switch(type):
 *     ACTUAL: redirectTo is absent,
 *             header[2]: recordSizeType =(SMALL | LARGE)
 *             switch(recordSizeType):
 *                 SMALL(header: 2 bytes):
 *                       capacity = header[3..7]*8 + 6          =[6..254]
 *                       length   = header[8..15]               =[0..256]
 *                 LARGE(header: 5 bytes):
 *                       capacity = header[3..7]+[8..19] * 8 + 3 [3..1048_579]
 *                       length   = [20..40]                     [0..1048_576]
 *     MOVED: data is absent, length is absent (=0)
 *            header: 7bytes
 *            capacity = header[2..7][8..23] * 8 + 1             [1.. 2^24+1]
 *            redirectToId = header[24..55]                      [32 bits]
 *     PADDING: data is absent, length is absent (=capacity), redirectTo is absent
 *            header: 3 byte
 *            capacity = header[2..23]*8 + 5                     =[5..33_554_437]
 *     PARTIAL: data is absent, length is absent (=capacity)
 *            header: 7 bytes
 *            capacity = header[2..7][8..23] * 8 + 1             [1.. 2^24+1]
 *            nextPartId = header[24..55]                        [32 bits]
 * </pre>
 */
abstract class RecordLayout {
  public static final byte RECORD_TYPE_MASK = (byte)0b1100_0000;

  public static final byte RECORD_TYPE_ACTUAL = (byte)0b0000_0000;
  public static final byte RECORD_TYPE_MOVED = (byte)0b0100_0000;
  public static final byte RECORD_TYPE_PADDING = (byte)0b1000_0000;
  public static final byte RECORD_TYPE_PARTIAL = (byte)0b1100_0000;


  /**
   * Use offsets stepping with OFFSET_BUCKET -- this allows to address OFFSET_BUCKET times more bytes with
   * int offset (at the cost of more sparse disk/memory representation)
   */
  public static final int OFFSET_BUCKET = 8;
  public static final int OFFSET_BUCKET_BITS = 3;

  /** @return one of RECORD_TYPE_XXX constants */
  public static byte recordType(final ByteBuffer source,
                                final int offset) {
    final byte headerByte0 = source.get(offset);
    return recordType(headerByte0);
  }

  /** @return one of RECORD_TYPE_XXX constants */
  public static byte recordType(final byte headerByte0) {
    return (byte)(headerByte0 & RECORD_TYPE_MASK);
  }

  public static RecordLayout recordLayout(final ByteBuffer source,
                                          final int offset) {
    final byte headerByte0 = source.get(offset);
    final byte recordType = recordType(headerByte0);
    return switch (recordType) {
      case RECORD_TYPE_ACTUAL -> recordLayoutForType(ActualRecords.recordSizeType(headerByte0));
      case RECORD_TYPE_MOVED -> MovedRecord.INSTANCE;
      case RECORD_TYPE_PADDING -> PaddingRecord.INSTANCE;
      case RECORD_TYPE_PARTIAL -> throw new UnsupportedOperationException("RECORD_TYPE_PARTIAL is not supported yet");
      default -> throw new AssertionError("Bug: type " + recordType + " is unknown");
    };
  }

  /** @return one of RECORD_TYPE_XXX constants */
  public abstract byte recordType();

  public abstract int headerSize();

  public abstract void putRecord(final ByteBuffer target,
                                 final int offset,
                                 final int capacity,
                                 final int length,
                                 final int redirectTo,
                                 final ByteBuffer payload);

  public void putLength(final ByteBuffer buffer,
                        final int offset,
                        final int newLength) {
    throw new UnsupportedOperationException("Method not implemented for " + getClass());
  }

  public abstract int capacity(final ByteBuffer source,
                               final int offset);

  public abstract int length(final ByteBuffer source,
                             final int offset);

  public int redirectToId(final ByteBuffer source,
                          final int offset) {
    return StreamlinedBlobStorage.NULL_ID;
  }

  public int fullRecordSize(final int capacity) {
    return headerSize() + capacity;
  }

  static final class ActualRecords {
    //ACTUAL: has .length and .capacity fields in header (redirectTo is absent)
    //        header bit[2]: recordSizeType =(SMALL | LARGE)
    //        length & capacity stored differently, depending on recordSizeType

    private static final byte RECORD_SIZE_TYPE_MASK = (byte)0b0010_0000;

    private static final byte RECORD_SIZE_TYPE_LARGE = (byte)0b0010_0000;
    private static final byte RECORD_SIZE_TYPE_SMALL = (byte)0b0000_0000;

    public static byte recordSizeType(final byte headerByte0) {
      return (byte)(headerByte0 & RECORD_SIZE_TYPE_MASK);
    }

    public static byte recordSizeTypeByCapacity(final int capacity) {
      if (capacity <= ActualRecords.SmallRecord.MAX_CAPACITY) {
        return RECORD_SIZE_TYPE_SMALL;
      }
      else if (capacity <= ActualRecords.LargeRecord.MAX_CAPACITY) {
        return RECORD_SIZE_TYPE_LARGE;
      }
      throw new IllegalArgumentException("capacity(=" + capacity + ") is too large for a storage");
    }

    public static RecordLayout recordLayoutForType(final byte recordSizeType) {
      return switch (recordSizeType) {
        case RECORD_SIZE_TYPE_SMALL -> ActualRecords.SmallRecord.INSTANCE;
        case RECORD_SIZE_TYPE_LARGE -> ActualRecords.LargeRecord.INSTANCE;
        default -> throw new IllegalArgumentException("recordSizeType(=" + recordSizeType + ") is unknown");
      };
    }

    static final class SmallRecord extends RecordLayout {
      //recordSizeType: SMALL => header: 2 bytes
      //    capacity = headerByte0[3..7]*8 + 6          =[6..254]
      //    length   = headerByte1                      =[0..256] (truncated to capacity)

      public static final ActualRecords.SmallRecord INSTANCE = new ActualRecords.SmallRecord();

      public static final int HEADER_SIZE = 2;

      public static final byte CAPACITY_MASK = (byte)0b00_01_1111;

      public static final int MIN_CAPACITY = OFFSET_BUCKET - HEADER_SIZE;
      public static final int MAX_CAPACITY = (CAPACITY_MASK << OFFSET_BUCKET_BITS) + MIN_CAPACITY;

      @Override
      public void putRecord(final ByteBuffer target,
                            final int offset,
                            final int capacity,
                            final int length,
                            final int redirectTo,
                            final ByteBuffer payload) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
          throw new IllegalArgumentException("capacity(" + capacity + ") must be in [" + MIN_CAPACITY + ".." + MAX_CAPACITY + "]");
        }
        if (length < 0 || length > MAX_CAPACITY) {
          throw new IllegalArgumentException("length(" + length + ") must be in [0, " + MAX_CAPACITY + "]");
        }
        final int capacityOverFirstBucket = capacity - MIN_CAPACITY;
        if ((capacityOverFirstBucket % OFFSET_BUCKET) != 0) {
          throw new IllegalArgumentException("capacity-MIN (=" + capacityOverFirstBucket + ") must be rounded up to " + OFFSET_BUCKET);
        }
        final int packedCapacity = capacityOverFirstBucket >> OFFSET_BUCKET_BITS;
        final byte headerByte0 = (byte)(RECORD_TYPE_ACTUAL | RECORD_SIZE_TYPE_SMALL | packedCapacity);
        final byte headerByte1 = (byte)length;
        target.put(offset, headerByte0);
        target.put(offset + 1, headerByte1);
        target.put(offset + HEADER_SIZE, payload, payload.position(), length);
      }


      @Override
      public void putLength(final ByteBuffer target,
                            final int offset,
                            final int newLength) {
        if (newLength < 0 || newLength > MAX_CAPACITY) {
          throw new IllegalArgumentException("length(" + newLength + ") must be in [0, " + MAX_CAPACITY + "]");
        }
        final byte headerByte1 = (byte)newLength;
        target.put(offset + 1, headerByte1);
      }

      @Override
      public int capacity(final ByteBuffer source,
                          final int offset) {
        final byte headerByte0 = source.get(offset);
        return capacity(headerByte0);
      }

      @Override
      public int length(final ByteBuffer source,
                        final int offset) {
        final byte headerByte1 = source.get(offset + 1);
        return Byte.toUnsignedInt(headerByte1);
      }

      public static int capacity(final byte headerByte0) {
        final byte recordSizeType = recordSizeType(headerByte0);
        if (recordSizeType != RECORD_SIZE_TYPE_SMALL) {
          throw new IllegalArgumentException("headerByte0(" + headerByte0 + ") doesn't encode SMALL record!");
        }
        return ((headerByte0 & CAPACITY_MASK) << OFFSET_BUCKET_BITS) + MIN_CAPACITY;
      }

      public static int length(final byte headerByte0,
                               final byte headerByte1) {
        return Byte.toUnsignedInt(headerByte1);
      }

      @Override
      public byte recordType() {
        return RECORD_TYPE_ACTUAL;
      }

      @Override
      public int headerSize() {
        return HEADER_SIZE;
      }
    }

    static final class LargeRecord extends RecordLayout {
      //recordSizeType: LARGE => header: 5 bytes
      //    capacity = header bits[3..7]+[8..19] * 8 + 3   [3..1048_579]
      //    length   = header bits[20..40]                 [0..1048_576]

      public static final ActualRecords.LargeRecord INSTANCE = new ActualRecords.LargeRecord();

      public static final int HEADER_SIZE = 5;

      private static final byte CAPACITY_MASK_0 = (byte)0b0001_1111;
      private static final byte CAPACITY_BITS_0 = 5;
      private static final int CAPACITY_MASK_1 = 0b1111_1111_1111;
      private static final int CAPACITY_BITS_1 = 12;// +5 bits of byte0 = 17 bits total

      private static final int LENGTH_BITS = 20;
      private static final int LENGTH_MASK = 0b1111_1111_1111_1111_1111; //20 bits

      public static final int MIN_CAPACITY = OFFSET_BUCKET - HEADER_SIZE;
      /**
       * Cut 1 bucket off the possible max, since length (20bit, max: 1048_576) is < possible max capacity (1048_579)
       * and it is meaningless to allow excess capacity that couldn't be used.
       */
      public static final int MAX_CAPACITY = ((1 << 20) - OFFSET_BUCKET) + MIN_CAPACITY;

      @Override
      public byte recordType() {
        return RECORD_TYPE_ACTUAL;
      }

      @Override
      public int headerSize() {
        return HEADER_SIZE;
      }

      @Override
      public void putRecord(final ByteBuffer target,
                            final int offset,
                            final int capacity,
                            final int length,
                            final int redirectTo,
                            final ByteBuffer payload) {
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
          throw new IllegalArgumentException("capacity(" + capacity + ") must be in [" + MIN_CAPACITY + ".." + MAX_CAPACITY + "]");
        }
        if (length < 0 || length > MAX_CAPACITY) {
          throw new IllegalArgumentException("length(" + length + ") must be in [0, " + MAX_CAPACITY + "]");
        }
        final int capacityOverFirstBucket = capacity - MIN_CAPACITY;
        if ((capacityOverFirstBucket % OFFSET_BUCKET) != 0) {
          throw new IllegalArgumentException("capacity-MIN (=" + capacityOverFirstBucket + ") must be rounded up to " + OFFSET_BUCKET);
        }
        final int packedCapacity = capacityOverFirstBucket >> OFFSET_BUCKET_BITS;
        final int first5BitsOfCapacity = packedCapacity >> CAPACITY_BITS_1;
        final byte headerByte0 = (byte)(RECORD_TYPE_ACTUAL | RECORD_SIZE_TYPE_LARGE | first5BitsOfCapacity);

        final int headerBytes1_4 = Integer.rotateRight(packedCapacity & CAPACITY_MASK_1, CAPACITY_BITS_1)
                                   | (length & LENGTH_MASK);

        target.put(offset, headerByte0);
        target.putInt(offset + 1, headerBytes1_4);
        target.put(offset + HEADER_SIZE, payload, payload.position(), length);
      }

      @Override
      public void putLength(final ByteBuffer target,
                            final int offset,
                            final int length) {
        if (length < 0 || length > MAX_CAPACITY) {
          throw new IllegalArgumentException("length(" + length + ") must be in [0, " + MAX_CAPACITY + "]");
        }
        final int headerBytes1_4 = target.getInt(offset + 1);
        final int headerBytes1_4_New = (headerBytes1_4 & (~LENGTH_MASK)) | (length & LENGTH_MASK);
        target.putInt(offset + 1, headerBytes1_4_New);
      }

      @Override
      public int capacity(final ByteBuffer source,
                          final int offset) {
        final byte headerByte0 = source.get(offset);
        final int headerBytes1_4 = source.getInt(offset + 1);
        return capacity(headerByte0, headerBytes1_4);
      }

      @Override
      public int length(final ByteBuffer source,
                        final int offset) {
        final int headerBytes1_4 = source.getInt(offset + 1);
        return headerBytes1_4 & LENGTH_MASK;
      }

      public static int capacity(final byte headerByte0,
                                 final int headerBytes1_4) {
        final byte recordSizeType = recordSizeType(headerByte0);
        if (recordSizeType != RECORD_SIZE_TYPE_LARGE) {
          throw new IllegalArgumentException("headerByte0(" + headerByte0 + ") doesn't encode LARGE record!");
        }
        final int first5bits = headerByte0 & CAPACITY_MASK_0;
        final int next12bits = (headerBytes1_4 >> LENGTH_BITS) & CAPACITY_MASK_1;
        final int allBits = (first5bits << CAPACITY_BITS_1) | next12bits;
        return (allBits << 3) + MIN_CAPACITY;
      }
    }
  }

  static final class MovedRecord extends RecordLayout {
    // MOVED: header: 7bytes (no .payload, no .length)
    //        capacity = header[2..7][8..23] * 8 + 1             [1.. 2^24+1]
    //        redirectToId = header[24..55]                      [32 bits]

    public static final MovedRecord INSTANCE = new MovedRecord();

    public static final int HEADER_SIZE = 7;//capacity(1 + 2) + redirectId(4)

    private static final byte CAPACITY_MASK_0 = 0b0011_1111;
    private static final byte CAPACITY_BITS_0 = 6;
    private static final int CAPACITY_MASK_1_2 = 0b1111_1111_1111_1111;
    private static final int CAPACITY_BITS_1_2 = 16;// +6 bits of byte0 = 22 bits total

    private static final int REDIRECT_TO_OFFSET = 3;

    public static final int MIN_CAPACITY = OFFSET_BUCKET - HEADER_SIZE;

    public static final int MAX_CAPACITY = (((1 << (CAPACITY_BITS_0 + CAPACITY_BITS_1_2)) - 1) << OFFSET_BUCKET_BITS)
                                           + MIN_CAPACITY;


    @Override
    public byte recordType() {
      return RECORD_TYPE_MOVED;
    }

    @Override
    public int headerSize() {
      return HEADER_SIZE;
    }

    @Override
    public void putRecord(final ByteBuffer target,
                          final int offset,
                          final int capacity,
                          final int length,
                          final int redirectToId,
                          final ByteBuffer payload) {
      if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
        throw new IllegalArgumentException("capacity(" + capacity + ") must be in [" + MIN_CAPACITY + ".." + MAX_CAPACITY + "]");
      }
      if (length != 0) {
        throw new IllegalArgumentException("length(" + length + ") must be in 0 for MOVED records");
      }
      final int capacityOverFirstBucket = capacity - MIN_CAPACITY;
      if ((capacityOverFirstBucket % OFFSET_BUCKET) != 0) {
        throw new IllegalArgumentException("capacity-MIN (=" + capacityOverFirstBucket + ") must be rounded up to " + OFFSET_BUCKET);
      }

      final int packedCapacity = capacityOverFirstBucket >> OFFSET_BUCKET_BITS;
      final int highest6BitsOfCapacity = packedCapacity >> CAPACITY_BITS_1_2;
      final byte headerByte0 = (byte)(RECORD_TYPE_MOVED | highest6BitsOfCapacity);

      final short headerBytes1_2 = (short)(packedCapacity & CAPACITY_MASK_1_2);


      target.put(offset, headerByte0);
      target.putShort(offset + 1, headerBytes1_2);
      target.putInt(offset + REDIRECT_TO_OFFSET, redirectToId);
    }

    public void putRedirectTo(final ByteBuffer target,
                              final int offset,
                              final int redirectToId) {
      target.putInt(offset + REDIRECT_TO_OFFSET, redirectToId);
    }

    @Override
    public int capacity(final ByteBuffer source,
                        final int offset) {
      final byte headerByte0 = source.get(offset);
      final short headerBytes1_2 = source.getShort(offset + 1);
      final int packedCapacity = ((headerByte0 & CAPACITY_MASK_0) << CAPACITY_BITS_1_2)
                                 | (headerBytes1_2 & CAPACITY_MASK_1_2);
      final int capacity = (packedCapacity << OFFSET_BUCKET_BITS)
                           + MIN_CAPACITY;
      assert capacity <= MAX_CAPACITY : "capacity(" + capacity + ") > MAX " + MAX_CAPACITY;
      return capacity;
    }

    @Override
    public int length(final ByteBuffer source,
                      final int offset) {
      return 0;
    }

    @Override
    public int redirectToId(final ByteBuffer source,
                            final int offset) {
      return source.getInt(offset + REDIRECT_TO_OFFSET);
    }
  }

  static final class PaddingRecord extends RecordLayout {
    // PADDING: header: 3 bytes (no .payload, no .length, no .redirectTo)
    //        capacity = header[2..7][8..15][16..23] * 8 + 5             [5..33_554_429]

    public static final PaddingRecord INSTANCE = new PaddingRecord();

    public static final int HEADER_SIZE = 3;

    private static final byte CAPACITY_MASK_0 = 0b0011_1111;
    private static final byte CAPACITY_BITS_0 = 6;
    private static final byte CAPACITY_BITS_1 = 8;
    private static final byte CAPACITY_BITS_2 = 8;
    private static final int CAPACITY_MASK_1 = 0b1111_1111;
    private static final int CAPACITY_MASK_2 = 0b1111_1111;

    public static final int MIN_CAPACITY = OFFSET_BUCKET - HEADER_SIZE;
    public static final int MAX_CAPACITY =
      (((1 << (CAPACITY_BITS_0 + CAPACITY_BITS_1 + CAPACITY_BITS_2)) - 1) << OFFSET_BUCKET_BITS) + MIN_CAPACITY;


    @Override
    public byte recordType() {
      return RECORD_TYPE_PADDING;
    }

    @Override
    public int headerSize() {
      return HEADER_SIZE;
    }

    @Override
    public void putRecord(final ByteBuffer target,
                          final int offset,
                          final int capacity,
                          final int length,
                          final int redirectToId,
                          final ByteBuffer payload) {
      if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
        throw new IllegalArgumentException(
          "PaddingRecord capacity(" + capacity + ") must be in [" + MIN_CAPACITY + ".." + MAX_CAPACITY + "]");
      }
      final int capacityOverFirstBucket = capacity - MIN_CAPACITY;
      if ((capacityOverFirstBucket % OFFSET_BUCKET) != 0) {
        throw new IllegalArgumentException("capacity-MIN (=" + capacityOverFirstBucket + ") must be rounded up to " + OFFSET_BUCKET);
      }

      final int packedCapacity = capacityOverFirstBucket >> OFFSET_BUCKET_BITS;

      final byte headerByte0 = (byte)(RECORD_TYPE_PADDING
                                      | (((packedCapacity >> CAPACITY_BITS_2) >> CAPACITY_BITS_1) & CAPACITY_MASK_0));
      final byte headerByte1 = (byte)((packedCapacity >> CAPACITY_BITS_2) & CAPACITY_MASK_1);
      final byte headerByte2 = (byte)(packedCapacity & CAPACITY_MASK_2);
      target.put(offset, headerByte0);
      target.put(offset + 1, headerByte1);
      target.put(offset + 2, headerByte2);
    }

    @Override
    public int capacity(final ByteBuffer source,
                        final int offset) {
      final byte headerByte0 = source.get(offset);
      final byte headerByte1 = source.get(offset + 1);
      final byte headerByte2 = source.get(offset + 2);
      final int packedCapacity = ((((headerByte0 & CAPACITY_MASK_0) << CAPACITY_BITS_1)
                                   | (headerByte1 & CAPACITY_MASK_1)) << CAPACITY_BITS_2)
                                 | (headerByte2 & CAPACITY_MASK_2);
      return (packedCapacity << OFFSET_BUCKET_BITS) + MIN_CAPACITY;
    }

    @Override
    public int length(final ByteBuffer source,
                      final int offset) {
      return 0;
    }
  }
}
