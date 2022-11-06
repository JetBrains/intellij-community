// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.Processor;
import com.intellij.util.io.AppendablePersistentMap.ValueDataAppender;
import org.jetbrains.annotations.*;

import java.io.*;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A base interface for custom persistent map implementations.
 * It is intentionally made not to extend other interfaces.
 * Wrap this class with {@link PersistentHashMap} if you need it to implement other interfaces
 *
 * @see PersistentHashMap
 * @see PersistentMapBuilder
 */
@ApiStatus.Experimental
public interface PersistentMapBase<Key, Value> {
  @ApiStatus.OverrideOnly
  @NotNull
  DataExternalizer<Value> getValuesExternalizer();

  /**
   * Appends value chunk from specified appender to key's value.
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get(Object)} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   */
  default void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    BufferExposingByteArrayOutputStream bos = new BufferExposingByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);
    DataExternalizer<Value> dataExternalizer = getValuesExternalizer();

    Value oldValue = get(key);
    if (oldValue != null) {
      dataExternalizer.save(dos, oldValue);
    }
    appender.append(dos);
    dos.close();

    DataInputStream dis = new DataInputStream(bos.toInputStream());
    Value newValue = dataExternalizer.read(dis);
    dis.close();

    put(key, newValue);
  }

  /**
   * Process only existing keys in a map.
   * TODO: do we have a constraint on ordering? Deterministic ordering?
   */
  boolean processExistingKeys(@NotNull Processor<? super Key> processor) throws IOException;

  /**
   * Process all keys registered in the map.
   * It several implementations it might work significantly faster then {@link PersistentMapBase#processExistingKeys(Processor)}
   * and it makes sense to prefer this method in performance critical code: completion, navigation.
   * <br>
   * Note that:
   * <ul><li>
   *  keys which were removed at some point might be returned as well.
   * </li><li>
   *  some implementations might not support it.
   * </li></ul>
   * TODO: shall we name it explicitly? Let's use default implementation for either of methods.
   * TODO: do we have constraint on ordering? Deterministic ordering?
   */
  boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException;

  boolean containsKey(Key key) throws IOException;

  Value get(Key key) throws IOException;

  void put(Key key, Value value) throws IOException;

  void remove(Key key) throws IOException;

  /**
   * Returns true if storage is dirty. See {@link PersistentMapBase#markDirty()} for details.
   */
  boolean isDirty();

  /**
   * Marks storage as dirty, e.g. it has some in memory changes which are not yet written to disk
   * or write in progress.
   */
  void markDirty() throws IOException;

  /**
   * Force all transient changes to disk.
   */
  void force() throws IOException;

  boolean isClosed();

  void close() throws IOException;

  /**
   * Closes the map removing all data storages.
   * Note, that map should not be used after this method has been called.
   */
  void closeAndDelete() throws IOException;

  /** @return keys count, or -1 if implementation doesn't provide this info */
  default int keysCount() {
    return -1;
  }

  /**
   * Method creates 'canonical' PMap by copying content of originalMap into canonicalMap (which assumed to be
   * empty).
   * <br/>
   * PHMap binary (on-disk) representation could be different even for the same key-value content because
   * of different order of inserts/deletes/updates. This method tries to generate PMap with 'canonical'
   * binary content. It is done by copying entries to canonicalMap in some stable order, defined by
   * stableKeysSorter, which guarantees canonicalMap to have fixed binary representation on disk.
   *
   * @param stableKeysSorter   function to sort List of keys. Must provide stable sort -- i.e. same set
   *                           of keys must always come out sorted in the same order, regardless of their
   *                           original order
   * @param targetCanonicalMap out-parameter: empty map of the same type as originalMap (i.e. suitable as
   *                           receiver of keys-values from originalMap)
   * @param valueCanonicalizer function to create canonicalized version of map values
   */
  static <K, V, M extends PersistentMapBase<? super K, ? super V>> M canonicalize(
    final @NotNull PersistentMapBase<K, V> originalMap,
    final @NotNull /* @OutParam */ M targetCanonicalMap,
    final @NotNull Function<List<K>, List<K>> stableKeysSorter,
    final @NotNull Function<V, V> valueCanonicalizer
  ) throws IOException {
    final List<K> keys = new ArrayList<>();
    originalMap.processExistingKeys(k -> {
      keys.add(k);
      return true;
    });

    final List<? extends K> sortedKeys = stableKeysSorter.apply(keys);
    for (K key : sortedKeys) {
      final V value = originalMap.get(key);
      final V canonicalizedValue = valueCanonicalizer.apply(value);
      targetCanonicalMap.put(key, canonicalizedValue);
    }
    return targetCanonicalMap;
  }

  static <K, V, M extends PersistentMapBase<? super K, ? super V>> M canonicalize(
    final @NotNull PersistentMapBase<K, V> originalMap,
    final @NotNull /* @OutParam */ M targetCanonicalMap,
    final @NotNull Function<List<K>, List<K>> stableKeysSorter) throws IOException {
    
    return canonicalize(originalMap, targetCanonicalMap, stableKeysSorter, Function.identity());
  }
}
