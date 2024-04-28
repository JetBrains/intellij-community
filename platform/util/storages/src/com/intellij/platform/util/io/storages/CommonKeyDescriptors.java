// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class CommonKeyDescriptors {
  private CommonKeyDescriptors() {
    throw new AssertionError("Bug: not for instantiation");
  }

  public static KeyDescriptorEx<Integer> integer() {
    return INTEGER_DESCRIPTOR;
  }

  public static KeyDescriptorEx<ByteArraySequence> byteArraySequence() {
    return BYTE_ARRAY_SEQUENCE_DESCRIPTOR;
  }

  public static KeyDescriptorEx<String> stringAsUTF8() {
    return STRING_AS_UTF8_DESCRIPTOR;
  }

  private static final KeyDescriptorEx<Integer> INTEGER_DESCRIPTOR = new KeyDescriptorEx<>() {
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
    public Integer read(@NotNull ByteBuffer input) throws IOException {
      return input.getInt();
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull Integer value) throws IOException {
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
      public ByteBuffer write(@NotNull ByteBuffer data) throws IOException {
        return data.putInt(value);
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
    public ByteArraySequence read(@NotNull ByteBuffer input) throws IOException {
      byte[] array = new byte[input.remaining()];
      input.get(array);
      return ByteArraySequence.create(array);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull ByteArraySequence value) throws IOException {
      return new KnownSizeRecordWriter() {
        @Override
        public ByteBuffer write(@NotNull ByteBuffer data) throws IOException {
          return data.put(value.getInternalBuffer(), value.getOffset(), value.length());
        }

        @Override
        public int recordSize() {
          return value.length();
        }
      };
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
    public @NotNull String read(@NotNull ByteBuffer input) throws IOException {
      return IOUtil.readString(input);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull String key) throws IOException {
      return DataExternalizerEx.fromBytes(key.getBytes(UTF_8));
    }

    @Override
    public String toString() {
      return "StringAsUTF8";
    }
  };
}
