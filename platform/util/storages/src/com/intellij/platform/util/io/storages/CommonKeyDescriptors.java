// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.io.ByteArraySequence;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.nio.charset.StandardCharsets.UTF_8;

public final class CommonKeyDescriptors {
  private CommonKeyDescriptors() {
    throw new AssertionError("Bug: not for instantiation");
  }

  public static InlineKeyDescriptorEx<Integer> integer() {
    return INTEGER_DESCRIPTOR;
  }

  public static KeyDescriptorEx<ByteArraySequence> byteArraySequence() {
    return BYTE_ARRAY_SEQUENCE_DESCRIPTOR;
  }

  public static KeyDescriptorEx<String> stringAsUTF8() {
    return STRING_AS_UTF8_DESCRIPTOR;
  }

  private static final InlineKeyDescriptorEx<Integer> INTEGER_DESCRIPTOR = new InlineKeyDescriptorEx<>() {
    @Override
    public int getHashCode(@NotNull Integer value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(@NotNull Integer key1,
                           @NotNull Integer key2) {
      return Objects.equals(key1, key2);
    }

    @Override
    public int toInt(@NotNull Integer value) {
      return value;
    }

    @Override
    public Integer fromInt(int code) {
      return code;
    }

    @Override
    public Integer read(@NotNull MemorySegment input) {
      return input.get(JAVA_INT_UNALIGNED, 0);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull Integer value) {
      return new IntegerRecordWriter(value.intValue());
    }

    @Override
    public int recordSizeIfConstant() {
      return Integer.BYTES;
    }

    @Override
    public String toString() {
      return "KeyDescriptorEx[Int32]";
    }

    private record IntegerRecordWriter(int value) implements KnownSizeRecordWriter {
      @Override
      public void write(@NotNull MemorySegment target, long startingOffset) {
        target.set(JAVA_INT_UNALIGNED, startingOffset, value);
      }

      @Override
      public int recordSize() {
        return Integer.BYTES;
      }
    }
  };


  private static final KeyDescriptorEx<ByteArraySequence> BYTE_ARRAY_SEQUENCE_DESCRIPTOR = new KeyDescriptorEx<>() {
    @Override
    public int getHashCode(@NotNull ByteArraySequence value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(@NotNull ByteArraySequence key1,
                           @NotNull ByteArraySequence key2) {
      return Objects.equals(key1, key2);
    }

    @Override
    public ByteArraySequence read(@NotNull MemorySegment input) {
      return ByteArraySequence.create(input.toArray(JAVA_BYTE));
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull ByteArraySequence value) {
      return DataExternalizerEx.fromBytes(value);
    }

    @Override
    public String toString() {
      return "KeyDescriptorEx[ByteArraySequence]";
    }
  };

  /** Stores/loads strings as standard UTF8 bytes */
  private static final KeyDescriptorEx<String> STRING_AS_UTF8_DESCRIPTOR = new KeyDescriptorEx<>() {
    @Override
    public int getHashCode(@NotNull String value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(@NotNull String key1,
                           @NotNull String key2) {
      return key1.equals(key2);
    }

    @Override
    public @NotNull String read(@NotNull MemorySegment input) {
      return new String(input.toArray(JAVA_BYTE), UTF_8);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull String key) {
      return DataExternalizerEx.fromBytes(key.getBytes(UTF_8));
    }

    @Override
    public String toString() {
      return "StringAsUTF8";
    }
  };
}
