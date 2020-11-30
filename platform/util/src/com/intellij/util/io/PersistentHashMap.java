// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;

import static com.intellij.util.io.PersistentHashMapBuilder.newBuilder;

/**
 * A delegate for a Persistent Hash Map (PHM) implementation
 * <p/>
 * This class plays several roles to preserve backward API compatibility:
 * <ul>
 *   <li>base interface for {@link PersistentHashMap}, so please use that one in any public or open API</li>
 *   <li>it delegates all calls to {@link PersistentHashMapBase} implementation</li>
 *   <li>factory adapter for backward compatibility - constructors delegates to {@link PersistentHashMapBuilder} to create the best implementation</li>
 * </ul>
 *
 * @implNote Please to not override this class, it is not final to preserve backward compatibility.
 * @see PersistentHashMapBuilder
 **/
public class PersistentHashMap<Key, Value> implements AppendablePersistentMap<Key, Value> {
  @NonNls
  static String DATA_FILE_EXTENSION = ".values";

  @NotNull private final PersistentHashMapBase<Key, Value> myImpl;

  PersistentHashMap(@NotNull PersistentHashMapBuilder<Key, Value> builder, boolean checkInheritedMembers) throws IOException {
    if (checkInheritedMembers) {
      builder.withReadonly(isReadOnly());
      builder.inlineValues(inlineValues());
    }
    myImpl = builder.buildImplementation();
  }

  public PersistentHashMap(@NotNull PersistentHashMapBase<Key, Value> impl) {
    myImpl = impl;
  }

  @Override
  public boolean isCorrupted() {
    //note: this method used in Scala plugin
    return myImpl.isCorrupted();
  }

  @Override
  public void deleteMap() {
    myImpl.deleteMap();
  }

  public PersistentHashMap(@NotNull File file,
                           @NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer) throws IOException {
    this(newBuilder(file.toPath(), keyDescriptor, valueExternalizer), true);
  }

  public PersistentHashMap(@NotNull Path file,
                           @NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer) throws IOException {
    this(newBuilder(file, keyDescriptor, valueExternalizer), true);
  }

  public PersistentHashMap(@NotNull Path file,
                           @NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer,
                           int initialSize) throws IOException {
    this(newBuilder(file, keyDescriptor, valueExternalizer).withInitialSize(initialSize), true);
  }

  public PersistentHashMap(@NotNull Path file,
                           @NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer,
                           int initialSize,
                           int version) throws IOException {
    this(newBuilder(file, keyDescriptor, valueExternalizer).withInitialSize(initialSize).withVersion(version), true);
  }

  public PersistentHashMap(@NotNull Path file,
                           @NotNull KeyDescriptor<Key> keyDescriptor,
                           @NotNull DataExternalizer<Value> valueExternalizer,
                           int initialSize,
                           int version,
                           @Nullable StorageLockContext lockContext) throws IOException {
    this(newBuilder(file, keyDescriptor, valueExternalizer).withInitialSize(initialSize).withVersion(version).withStorageLockContext(lockContext), true);
  }

  /**
   * @deprecated Please use {@link PersistentHashMapBuilder} instead
   */
  @Deprecated
  protected boolean inlineValues() {
    return false;
  }

  /**
   * @deprecated Please use {@link PersistentHashMapBuilder} instead
   */
  @Deprecated
  protected boolean isReadOnly() {
    return false;
  }

  public final void dropMemoryCaches() {
    myImpl.dropMemoryCaches();
  }

  public static void deleteFilesStartingWith(@NotNull File prefixFile) {
    IOUtil.deleteAllFilesStartingWith(prefixFile);
  }

  /**
   * Deletes {@param map} files and trying to close it before.
   */
  public static void deleteMap(@NotNull PersistentHashMap<?, ?> map) {
    map.myImpl.deleteMap();
  }

  @Override
  public final void put(Key key, Value value) throws IOException {
    myImpl.put(key, value);
  }

  /**
   * @deprecated please use {@link AppendablePersistentMap.ValueDataAppender}
   */
  @Deprecated
  @ApiStatus.Experimental
  @SuppressWarnings("DeprecatedIsStillUsed")
  public interface ValueDataAppender extends AppendablePersistentMap.ValueDataAppender {
  }

  /**
   * @deprecated please use {@link AppendablePersistentMap.ValueDataAppender} as the second parameter
   */
  @Deprecated
  @SuppressWarnings("LambdaUnfriendlyMethodOverload")
  public final void appendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    myImpl.appendData(key, appender);
  }

  /**
   * Appends value chunk from specified appender to key's value.
   * Important use note: value externalizer used by this map should process all bytes from DataInput during deserialization and make sure
   * that deserialized value is consistent with value chunks appended.
   * E.g. Value can be Set of String and individual Strings can be appended with this method for particular key, when {@link #get(Object)} will
   * be eventually called for the key, deserializer will read all bytes retrieving Strings and collecting them into Set
   */
  @Override
  public final void appendData(Key key, @NotNull AppendablePersistentMap.ValueDataAppender appender) throws IOException {
    myImpl.appendData(key, appender);
  }

  /**
   * Process all keys registered in the map. Note that keys which were removed after
   * {@link PersistentHashMapImpl#compact()} call will be processed as well. Use
   * {@link #processKeysWithExistingMapping(Processor)} to process only keys with existing mappings
   */
  @Override
  public final boolean processKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return myImpl.processKeys(processor);
  }

  @Override
  public final boolean isClosed() {
    return myImpl.isClosed();
  }

  @Override
  public final boolean isDirty() {
    return myImpl.isDirty();
  }

  @Override
  public final void markDirty() throws IOException {
    myImpl.markDirty();
  }

  @NotNull
  public final Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    return myImpl.getAllKeysWithExistingMapping();
  }

  public final boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException {
    return myImpl.processKeysWithExistingMapping(processor);
  }

  @Override
  public final Value get(Key key) throws IOException {
    return myImpl.get(key);
  }

  @Override
  public final boolean containsMapping(Key key) throws IOException {
    return myImpl.containsMapping(key);
  }

  public final void remove(Key key) throws IOException {
    myImpl.remove(key);
  }

  @Override
  public final void force() {
    myImpl.force();
  }

  @Override
  public final void close() throws IOException {
    myImpl.close();
  }

  @Override
  public final String toString() {
    return myImpl.toString();
  }
}
