// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Objects;

import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_CHAR_UNALIGNED;
import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_INT_UNALIGNED;
import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_LONG_UNALIGNED;
import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_SHORT_UNALIGNED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataExternalizerExTest {
  private static final DataExternalizerEx<Integer> NATIVE_INT_EXTERNALIZER = new DataExternalizerEx<>() {
    @Override
    public Integer read(@NotNull MemorySegment input) {
      assertEquals(Integer.BYTES, input.byteSize(), "The reader must receive one exact record slice");
      return input.get(JAVA_INT_UNALIGNED, 0);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull Integer value) {
      return new KnownSizeRecordWriter() {
        @Override
        public void write(@NotNull MemorySegment target, long startingOffset) {
          Objects.checkFromIndexSize(startingOffset, recordSize(), target.byteSize());
          target.set(JAVA_INT_UNALIGNED, startingOffset, value);
        }

        @Override
        public int recordSize() {
          return Integer.BYTES;
        }
      };
    }

    @Override
    public int recordSizeIfConstant() {
      return Integer.BYTES;
    }
  };

  @Test
  void readReceivesTheExactRecordSlice() throws IOException {
    byte[] storage = {11, 12, 13, 14, 15, 16};
    var record = MemorySegment.ofArray(storage).asSlice(1, Integer.BYTES);

    int value = NATIVE_INT_EXTERNALIZER.read(record);

    assertEquals(ByteBuffer.wrap(storage, 1, Integer.BYTES).order(ByteOrder.nativeOrder()).getInt(), value);
  }

  @Test
  void writerWritesAtANonZeroUnalignedOffsetInNativeOrder() throws IOException {
    int value = 0x01020304;
    byte[] storage = {41, 42, 43, 44, 45, 46};

    CommonKeyDescriptors.integer().writerFor(value).write(MemorySegment.ofArray(storage), 1);

    byte[] expectedValue = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.nativeOrder()).putInt(value).array();
    assertArrayEquals(new byte[]{41, expectedValue[0], expectedValue[1], expectedValue[2], expectedValue[3], 46}, storage);
    assertEquals(value, CommonKeyDescriptors.integer().read(MemorySegment.ofArray(storage).asSlice(1, Integer.BYTES)));
  }

  @Test
  void byteArrayWriterRejectsAShortTargetBeforeItChangesData() {
    var writer = DataExternalizerEx.fromBytes(new byte[]{1, 2, 3});
    byte[] storage = {11, 12, 13, 14};

    assertThrows(IndexOutOfBoundsException.class, () -> writer.write(MemorySegment.ofArray(storage), 2));

    assertArrayEquals(new byte[]{11, 12, 13, 14}, storage, "A failed range check must preserve the target");
  }

  @Test
  void constantRecordSizeMatchesEveryWriter() throws IOException {
    assertTrue(NATIVE_INT_EXTERNALIZER.isRecordSizeConstant());
    assertEquals(Integer.BYTES, NATIVE_INT_EXTERNALIZER.recordSizeIfConstant());
    assertEquals(Integer.BYTES, NATIVE_INT_EXTERNALIZER.writerFor(0).recordSize());
    assertEquals(Integer.BYTES, NATIVE_INT_EXTERNALIZER.writerFor(Integer.MAX_VALUE).recordSize());

    assertFalse(DataExternalizerEx.adapt(new StringExternalizer()).isRecordSizeConstant());
  }

  @Test
  void byteArraySequenceWriterUsesItsInternalOffset() throws IOException {
    byte[] source = {71, 72, 73, 74, 75};
    var writer = DataExternalizerEx.fromBytes(new ByteArraySequence(source, 1, 3));
    byte[] target = {10, 11, 12, 13, 14};

    writer.write(MemorySegment.ofArray(target), 1);

    assertEquals(3, writer.recordSize());
    assertArrayEquals(new byte[]{10, 72, 73, 74, 14}, target);
  }

  @Test
  void dataExternalizerAdapterKeepsTheStreamFormat() throws IOException {
    var externalizer = DataExternalizerEx.adapt(new StringExternalizer());
    var writer = externalizer.writerFor("record");
    var target = MemorySegment.ofArray(new byte[writer.recordSize()]);

    writer.write(target, 0);

    assertEquals("record", externalizer.read(target));
  }

  @Test
  void keyDescriptorAdapterKeepsEqualityAndSerialization() throws IOException {
    var descriptor = new CaseInsensitiveStringDescriptor();
    assertInstanceOf(KeyDescriptorEx.class, DataExternalizerEx.adapt(descriptor));
    var adapted = KeyDescriptorEx.adapt(descriptor);

    assertTrue(adapted.isEqual("KEY", "key"));
    assertEquals(descriptor.getHashCode("KEY"), adapted.getHashCode("key"));

    var writer = adapted.writerFor("Key");
    var target = MemorySegment.ofArray(new byte[writer.recordSize()]);
    writer.write(target, 0);
    assertEquals("Key", adapted.read(target));
  }

  @Test
  void persistentLayoutsUseNativeOrderAndAllowUnalignedAccess() {
    assertEquals(ByteOrder.nativeOrder(), JAVA_SHORT_UNALIGNED.order());
    assertEquals(ByteOrder.nativeOrder(), JAVA_CHAR_UNALIGNED.order());
    assertEquals(ByteOrder.nativeOrder(), JAVA_INT_UNALIGNED.order());
    assertEquals(ByteOrder.nativeOrder(), JAVA_LONG_UNALIGNED.order());
    assertEquals(1, JAVA_SHORT_UNALIGNED.byteAlignment());
    assertEquals(1, JAVA_CHAR_UNALIGNED.byteAlignment());
    assertEquals(1, JAVA_INT_UNALIGNED.byteAlignment());
    assertEquals(1, JAVA_LONG_UNALIGNED.byteAlignment());
  }

  private static class StringExternalizer implements DataExternalizer<String> {
    @Override
    public void save(@NotNull DataOutput out, String value) throws IOException {
      out.writeUTF(value);
    }

    @Override
    public String read(@NotNull DataInput in) throws IOException {
      return in.readUTF();
    }
  }

  private static final class CaseInsensitiveStringDescriptor extends StringExternalizer implements KeyDescriptor<String> {
    @Override
    public int getHashCode(String value) {
      return value.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public boolean isEqual(String first, String second) {
      return first.equalsIgnoreCase(second);
    }
  }
}
