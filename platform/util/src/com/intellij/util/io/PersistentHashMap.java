// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.util.io.PersistentMapBuilder.newBuilder;

/**
 * A delegate for a Persistent Hash Map (PHM) implementation
 * <p/>
 * This class plays several roles to preserve backward API compatibility:
 * <ul>
 *   <li>base interface for {@link PersistentHashMap}, so please use that one in any public or open API</li>
 *   <li>it delegates all calls to {@link PersistentMapBase} implementation</li>
 *   <li>factory adapter for backward compatibility - constructors delegates to {@link PersistentMapBuilder} to create the best implementation</li>
 * </ul>
 *
 * @implNote Please to not override this class, it is not final to preserve backward compatibility.
 * @see PersistentMapBuilder
 **/
public class PersistentHashMap<Key, Value> implements AppendablePersistentMap<Key, Value> {
  @NonNls
  static String DATA_FILE_EXTENSION = ".values";

  @NotNull private final PersistentMapBase<Key, Value> myImpl;

  PersistentHashMap(@NotNull PersistentMapBuilder<Key, Value> builder, boolean checkInheritedMembers) throws IOException {
    if (checkInheritedMembers) {
      builder.withReadonly(false);
      builder.inlineValues(false);
    }
    myImpl = builder.build().myImpl;
  }

  public PersistentHashMap(@NotNull PersistentMapBase<Key, Value> impl) {
    myImpl = impl;
  }

  @Override
  public final void closeAndClean() throws IOException {
    myImpl.closeAndDelete();
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

  public final void dropMemoryCaches() {
    force();
  }

  @Override
  public final void put(Key key, Value value) throws IOException {
    myImpl.put(key, value);
  }

  /**
   * @deprecated please use {@link AppendablePersistentMap.ValueDataAppender}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public interface ValueDataAppender extends AppendablePersistentMap.ValueDataAppender {
  }

  /**
   * @deprecated please use {@link AppendablePersistentMap.ValueDataAppender} as the second parameter
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
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
   * {@link PersistentMapImpl#compact()} call will be processed as well. Use
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

  /**
   * @deprecated use {@link PersistentHashMap#processKeysWithExistingMapping(Processor)} instead.
   */
  @Deprecated
  @NotNull
  public final Collection<Key> getAllKeysWithExistingMapping() throws IOException {
    List<Key> result = new ArrayList<>();
    myImpl.processExistingKeys(new CommonProcessors.CollectProcessor<>(result));
    return result;
  }

  public final boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException {
    return myImpl.processExistingKeys(processor);
  }

  @Override
  public final Value get(Key key) throws IOException {
    return myImpl.get(key);
  }

  @Override
  public final boolean containsMapping(Key key) throws IOException {
    return myImpl.containsKey(key);
  }

  @Override
  public final void remove(Key key) throws IOException {
    myImpl.remove(key);
  }

  @Override
  public final void force() {
    try {
      myImpl.force();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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
