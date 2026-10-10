// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.io.DataEnumerator;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

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
   * <p>
   * <p/>
   * Implementation is not 100% optimal -- it does unnecessary allocations and copying -- but usually good enough to
   * start using new API (i.e. {@link DataEnumerator}), and see does it make any difference.
   * <p/>
   * Still, one could do better by using more 'idiomatic' API -- but it takes more effort.
   */
  static <K> @NotNull KeyDescriptorEx<K> adapt(@NotNull KeyDescriptor<K> oldSchoolDescriptor) {
    Object candidate = oldSchoolDescriptor;
    //Do not wrap, if oldSchoolDescriptor already implements new interface
    // -> allows for 'bilingual' implementation (that implements both old&new
    // ifaces) to work efficiently
    if (candidate instanceof KeyDescriptorEx<?> exDescriptor) {
      //noinspection unchecked
      return (KeyDescriptorEx<K>)exDescriptor;
    }

    // Serialization/deserialization just bridges between MemorySegment and ByteArrayInput/OutputStream

    //MAYBE RC: one allocation & one copy could be removed by implementing something like MemorySegmentBackedInputStream
    //          instead of UnsyncByteArrayInputStream
    var externalizer = new StreamDataExternalizerExAdapter<>(oldSchoolDescriptor);
    return new KeyDescriptorEx<>() {
      @Override
      public int getHashCode(K value) {
        return oldSchoolDescriptor.getHashCode(value);
      }

      @Override
      public boolean isEqual(K key1, K key2) {
        return oldSchoolDescriptor.isEqual(key1, key2);
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
        return "KeyDescriptorExAdapter[adapted: " + oldSchoolDescriptor + "]";
      }
    };
  }
}
