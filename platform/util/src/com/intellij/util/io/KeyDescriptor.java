// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

/**
 * This interface provides necessary descriptions for key object of
 * persistable maps or indexes.
 * <br/>
 * Key objects may exhibit non-trivial equality and hashCode behaviours,
 * where two by-byte different serialized sequences yields the
 * {{@link #isEqual(Object, Object)}} objects.
 * <br/>
 * Extend the {@link DifferentSerializableBytesImplyNonEqualityPolicy}
 * in this interface to mark that by-byte in-equality of serialized objects
 * also imply deserialized keys in-equality via {@link #isEqual}.
 *
 * @see PersistentMap
 * @see PersistentHashMap
 * @see DifferentSerializableBytesImplyNonEqualityPolicy
 */
public interface KeyDescriptor<T> extends EqualityPolicy<T>, DataExternalizer<T> {
  /**
   * @implNote The implementation may use the returned hashcode values
   * together with {@link #save(DataOutput, Object)} to persist keys
   * on disk. Make sure the hashcode function is stable and returns
   * the stable values, e.g. that are independent from restarts or environment changes
   * @see #save(DataOutput, Object)
   */
  @Override
  int getHashCode(T value);

  /**
   * @implNote The implementation may use this method together
   * with {@link #getHashCode(Object)} to persist key objects on disk.
   * @see #getHashCode(Object)
   */
  @Override
  void save(@NotNull DataOutput out, T value) throws IOException;
}
