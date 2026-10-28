// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.InlineKeyDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

import static com.intellij.platform.util.io.storages.PersistentFormatLayouts.JAVA_INT_UNALIGNED;

/**
 * Full analog of {@link KeyDescriptor}, but with {@link MemorySegment} instead of {@link java.io.InputStream} and
 * {@link java.io.OutputStream}
 */
@ApiStatus.Internal
public interface KeyDescriptorEx<K> extends EqualityPolicy<K>, DataExternalizerEx<K> {
  /** See {@link KeyDescriptor#getHashCode(Object)} docs */
  @Override
  int getHashCode(K value);

  /** See {@link KeyDescriptor#isEqual(Object, Object)} docs */
  @Override
  boolean isEqual(K key1, K key2);

  /**
   * Adapts old-school {@link KeyDescriptor} to new {@link KeyDescriptorEx}.
   * If a legacyDescriptor also implements {@link KeyDescriptorEx} ('bilingual') -- it is returned as-is.
   * Otherwise, an adaptor is used: it reserves hash and equality rules, but serialization format is not guaranteed
   * to be binary-compatible with legacy one, and it is not guaranteed to be 100% optimal performance-wise.
   */
  static <K> @NotNull KeyDescriptorEx<K> adapt(@NotNull KeyDescriptor<K> legacyDescriptor) {
    Object candidate = legacyDescriptor;
    //Do not wrap, if legacyDescriptor already implements new interface -> allows for 'bilingual' implementation
    // (that implements both old&new ifaces) to work efficiently
    if (candidate instanceof KeyDescriptorEx<?> exDescriptor) {
      //noinspection unchecked
      return (KeyDescriptorEx<K>)exDescriptor;
    }

    // The branch is optional: the InlineKeyDescriptor branch also handles integers, but it is more explicit
    if (legacyDescriptor instanceof EnumeratorIntegerDescriptor) {
      //noinspection unchecked
      return (KeyDescriptorEx<K>)CommonKeyDescriptors.integer();
    }

    if (legacyDescriptor instanceof InlineKeyDescriptor<K> inlineDescriptor) {
      return new InlineKeyDescriptorEx<>() {
        @Override
        public int toInt(K key) {
          return inlineDescriptor.toInt(key);
        }

        @Override
        public K fromInt(int code) {
          return inlineDescriptor.fromInt(code);
        }

        @Override
        public K read(@NotNull MemorySegment input) {
          return fromInt(input.get(JAVA_INT_UNALIGNED, 0));
        }

        @Override
        public KnownSizeRecordWriter writerFor(@NotNull K key) {
          int code = toInt(key);
          return new KnownSizeRecordWriter() {
            @Override
            public void write(@NotNull MemorySegment target, long startingOffset) {
              target.set(JAVA_INT_UNALIGNED, startingOffset, code);
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

        @Override
        public String toString() {
          return "InlineKeyDescriptorExAdapter[adapted: " + legacyDescriptor + "]";
        }
      };
    }

    // Default adapter: serializes value into byte[]-backed InputStream, and save byte[] in MemorySegment: not very
    // effective, but usually good enough to start with
    var externalizer = new StreamDataExternalizerExAdapter<>(legacyDescriptor);
    return new KeyDescriptorEx<>() {
      @Override
      public int getHashCode(K value) {
        return legacyDescriptor.getHashCode(value);
      }

      @Override
      public boolean isEqual(K key1, K key2) {
        return legacyDescriptor.isEqual(key1, key2);
      }

      @Override
      public K read(@NotNull MemorySegment input) throws IOException {
        return externalizer.read(input);
      }

      @Override
      public KnownSizeRecordWriter writerFor(@NotNull K key) throws IOException {
        return externalizer.writerFor(key);
      }

      @Override
      public String toString() {
        return "KeyDescriptorExAdapter[adapted: " + legacyDescriptor + "]";
      }
    };
  }
}
