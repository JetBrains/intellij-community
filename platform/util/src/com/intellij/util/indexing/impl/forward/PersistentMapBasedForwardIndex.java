// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.IndexStorageLockingBase;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.MeasurableIndexStore;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.PersistentMap;
import com.intellij.util.io.PersistentMapBuilder;
import com.intellij.util.io.StorageLockContext;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * The implementation thread-safety relies on {@link PersistentMap} implementation used to be a thread-safe
 * (Which is true for the currently existing impls)
 */
@Internal
public final class PersistentMapBasedForwardIndex implements IterableForwardIndex, MeasurableIndexStore {

  private volatile @NotNull PersistentHashMap<Integer, ByteArraySequence> myPersistentMap;

  private final @NotNull Path myMapFile;
  private final boolean myUseChunks;
  private final boolean myReadOnly;
  private final @Nullable StorageLockContext myStorageLockContext;

  public PersistentMapBasedForwardIndex(@NotNull Path mapFile, boolean isReadOnly) throws IOException {
    this(mapFile, true, isReadOnly, null);
  }

  public PersistentMapBasedForwardIndex(@NotNull Path mapFile,
                                        boolean useChunks,
                                        boolean isReadOnly,
                                        @Nullable StorageLockContext storageLockContext) throws IOException {
    myPersistentMap = createMap(mapFile, useChunks, isReadOnly, storageLockContext);
    myStorageLockContext = storageLockContext;
    myMapFile = mapFile;
    myUseChunks = useChunks;
    myReadOnly = isReadOnly;
  }

  @Override
  public @Nullable ByteArraySequence get(@NotNull Integer key) throws IOException {
    return myPersistentMap.get(key);
  }

  @Override
  public <Key> boolean processFilteredKeys(@NotNull IntPredicate inputIdFilter,
                                           @NotNull AbstractMapForwardIndexAccessor<Key, ?, ?> accessor,
                                           @NotNull Processor<? super Key> processor) throws IOException {
    try {
      // Integer keys use InlinedKeyStorage, which doesn't support processKeys() (which is strange, but ce la vie)
      return myPersistentMap.processKeysWithExistingMapping(inputId -> {
        if (IndexStorageLockingBase.MAKE_INDEX_LOOKUP_CANCELLABLE) {
          // processor may be already cancellable, but with a lot of keys duplicated it could be called very rarely
          if ((inputId & 0b1111) == 0b1010) {// ~1/16
            Cancellation.checkCancelled();
          }
        }

        if (!inputIdFilter.test(inputId)) return true;

        try {
          Map<Key, ?> data = accessor.convertToInputDataMap(inputId, get(inputId));
          if (data != null) {
            for (Key key : data.keySet()) {
              if (!processor.process(key)) return false;
            }
          }
          return true;
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }
    catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  @Override
  public void put(@NotNull Integer key, @Nullable ByteArraySequence value) throws IOException {
    if (value == null) {
      myPersistentMap.remove(key);
    }
    else {
      myPersistentMap.put(key, value);
    }
  }

  @Override
  public void force() throws IOException {
    myPersistentMap.force();
  }

  @Override
  public boolean isDirty() {
    return myPersistentMap.isDirty();
  }

  @Override
  public int keysCountApproximately() {
    return MeasurableIndexStore.keysCountApproximatelyIfPossible(myPersistentMap);
  }

  @Override
  public void clear() throws IOException {
    myPersistentMap.closeAndClean();
    myPersistentMap = createMap(myMapFile, myUseChunks, myReadOnly, myStorageLockContext);
  }

  @Override
  public void close() throws IOException {
    myPersistentMap.close();
  }

  @Override
  public boolean isClosed() {
    return myPersistentMap.isClosed();
  }

  public boolean containsMapping(int key) throws IOException {
    return myPersistentMap.containsMapping(key);
  }

  public PersistentMap<Integer, ByteArraySequence> getUnderlyingMap(){
    return myPersistentMap;
  }

  private static @NotNull PersistentHashMap<Integer, ByteArraySequence> createMap(
    @NotNull Path file,
    boolean useChunks,
    boolean isReadOnly,
    @Nullable StorageLockContext storageLockContext
  ) throws IOException {
    return PersistentMapBuilder
      .newBuilder(file, EnumeratorIntegerDescriptor.INSTANCE, ByteSequenceDataExternalizer.INSTANCE)
      .hasChunks(useChunks)
      .withReadonly(isReadOnly)
      .withStorageLockContext(storageLockContext)
      .build();
  }
}
