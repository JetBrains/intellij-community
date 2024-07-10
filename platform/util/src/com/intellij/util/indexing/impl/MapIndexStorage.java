// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.util.io.PersistentHashMapValueStorage.CreationTimeOptions;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.Path;

@Internal
public class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>, MeasurableIndexStore {
  private static final Logger LOG = Logger.getInstance(MapIndexStorage.class);
  private static final boolean ENABLE_WAL = SystemProperties.getBooleanProperty("idea.index.enable.wal", false);

  private ValueContainerMap<Key, Value> myMap;

  private MapIndexStorageCache<Key, Value> myCache;

  protected final Path myBaseStorageFile;
  protected final KeyDescriptor<Key> myKeyDescriptor;
  private final int myCacheSize;

  private final DataExternalizer<Value> myDataExternalizer;
  /** {@link FileBasedIndexExtension#keyIsUniqueForIndexedFile} and {@link SingleEntryFileBasedIndexExtension} */
  private final boolean myKeyIsUniqueForIndexedFile;
  private final boolean myReadOnly;
  private final boolean myEnableWal;
  private final @NotNull ValueContainerInputRemapping myInputRemapping;

  public MapIndexStorage(Path storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile) throws IOException {
    this(storageFile, keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, true, false, false, null);
  }

  public MapIndexStorage(Path storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean keyIsUniqueForIndexedFile,
                         boolean initialize,
                         boolean readOnly,
                         boolean enableWal,
                         @Nullable ValueContainerInputRemapping inputRemapping) throws IOException {
    myBaseStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myCacheSize = cacheSize;
    myDataExternalizer = valueExternalizer;
    myKeyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    myReadOnly = readOnly;
    myEnableWal = enableWal;
    if (inputRemapping != null) {
      LOG.assertTrue(myReadOnly, "input remapping allowed only for read-only storage");
    }
    else {
      inputRemapping = ValueContainerInputRemapping.IDENTITY;
    }
    myInputRemapping = inputRemapping;
    if (initialize) initMapAndCache();
  }

  protected void initMapAndCache() throws IOException {
    ValueContainerMap<Key, Value> map = createValueContainerMap();
    myCache = MapIndexStorageCacheProvider.Companion.getActualProvider().createCache(
      key -> map.getModifiableValueContainer(key),
      (key, container) -> onDropFromCache(key, container),
      myKeyDescriptor,
      myCacheSize
    );
    myMap = map;
  }

  private void onDropFromCache(Key key, @NotNull ChangeTrackingValueContainer<Value> valueContainer) {
    try {
      if (myReadOnly || !valueContainer.isDirty()) {
        return;
      }

      if (myKeyIsUniqueForIndexedFile) {
        if (valueContainer.containsOnlyInvalidatedChange()) {
          myMap.remove(key);
          return;
        }

        //RC: afaicu, this is done just to ensure we do NOT use append-changes branch in a .merge().
        //    Append-changes is useless in keyIsUniqueForFile case because there is always <=1 (inputId, value) entry in
        //    ValueContainer, and at this point container could contain only 1 update change that container has changes (isDirty) and those changes are not removals
        //    (!containsOnlyInvalidatedChange) which (for keyIsUniqueForFile) implies there is 1 and only 1 added change
        if (valueContainer.containsCachedMergedData()) {
          valueContainer.setNeedsCompacting(true);
        }
      }
      myMap.merge(key, valueContainer);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected @NotNull PersistentMapBase<Key, UpdatableValueContainer<Value>> createPersistentMap(
    @NotNull KeyDescriptor<Key> keyDescriptor,
    @NotNull DataExternalizer<UpdatableValueContainer<Value>> valueContainerExternalizer,
    boolean isReadOnly,
    boolean compactOnClose,
    boolean keyIsUniqueForIndexedFile) throws IOException {

    CreationTimeOptions creationOptions = CreationTimeOptions.threadLocalOptions()
      .setCompactChunksWithValueDeserialization();
    if (keyIsUniqueForIndexedFile) {
      creationOptions = creationOptions.setHasNoChunks();
    }
    return creationOptions.with(() -> {
      PersistentMapBuilder<Key, UpdatableValueContainer<Value>> builder = PersistentMapBuilder
        .newBuilder(getStorageFile(), keyDescriptor, valueContainerExternalizer)
        .withReadonly(isReadOnly)
        .withCompactOnClose(compactOnClose);
      if (myEnableWal && ENABLE_WAL && !isReadOnly) {
        builder
          .withWal(true)
          .withWalExecutor(SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Index Wal Pool"));
      }
      return new PersistentMapImpl<>(builder);
    });
  }

  private @NotNull ValueContainerMap<Key, Value> createValueContainerMap() throws IOException {
    ValueContainerExternalizer<Value> valueContainerExternalizer = new ValueContainerExternalizer<>(myDataExternalizer, myInputRemapping);
    PersistentMapBase<Key, UpdatableValueContainer<Value>> persistentMap = createPersistentMap(myKeyDescriptor,
                                                                                               valueContainerExternalizer,
                                                                                               myReadOnly,
                                                                                               compactOnClose(),
                                                                                               myKeyIsUniqueForIndexedFile);

    return new ValueContainerMap<>(persistentMap,
                                   myKeyDescriptor,
                                   myDataExternalizer,
                                   myKeyIsUniqueForIndexedFile);
  }

  @Override
  public void updateValue(Key key, int inputId, Value newValue) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (myKeyIsUniqueForIndexedFile) {
        assertKeyInputIdConsistency(key, inputId);
        updateSingleValueDirectly(key, inputId, newValue);
      }
      else {
        IndexStorage.super.updateValue(key, inputId, newValue);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (myKeyIsUniqueForIndexedFile) {
        assertKeyInputIdConsistency(key, inputId);
        putSingleValueDirectly(key, inputId, value);
      }
      else {
        read(key).addValue(inputId, value);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    if (myReadOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    try {
      myMap.markDirty();
      if (myKeyIsUniqueForIndexedFile) {
        assertKeyInputIdConsistency(key, inputId);
        removeSingleValueDirectly(key, inputId);
      }
      else {
        // important: assuming the key exists in the index
        read(key).removeAssociatedValue(inputId);
      }
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  private @NotNull Path getStorageFile() {
    return getIndexStorageFile(myBaseStorageFile);
  }

  @Override
  public void flush() throws IOException {
    if (!myMap.isClosed()) {
      //TODO RC: inefficiency: we do need to _store_ all cached data -- but we don't want to clear the cache!
      //         With current implementation we get empty cache every time the flush() is called.
      invalidateCachedMappings();
      if (myMap.isDirty()) myMap.force();
    }
  }

  @Override
  public boolean isDirty() {
    if (myMap.isDirty()) {
      return true;
    }

    for (ChangeTrackingValueContainer<Value> container : myCache.getCachedValues()) {
      if (container.isDirty()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int keysCountApproximately() {
    return myMap.getStorageMap().keysCount();
  }

  protected boolean compactOnClose() {
    return false;
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, IOException::new,

      this::flush,
      myMap::close
    );
  }

  @Override
  @Internal
  public boolean isClosed() {
    return myMap.isClosed();
  }

  @Override
  public void clear() throws StorageException {
    try {
      myMap.closeAndDelete();
    }
    catch (Exception ignored) {
    }
    try {
      initMapAndCache();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      unwrapCauseAndRethrow(e);
    }
  }

  @Override
  public @NotNull ChangeTrackingValueContainer<Value> read(final Key key) throws StorageException {
    try {
      return myCache.read(key);
    }
    catch (RuntimeException e) {
      return unwrapCauseAndRethrow(e);
    }
  }

  /**
   * removes (key, inputId) tuple from index: special case there inputId is mapped to a single value.
   * We use artificial (key=inputId) for such case (see SingleEntryIndexer) so in that case key is
   * unique: only single inputId could have key(=inputId) => removing that key means there could be
   * no other (inputId,value) linked to it
   */
  private void removeSingleValueDirectly(Key key, int inputId) throws IOException {
    assert myKeyIsUniqueForIndexedFile;
    ChangeTrackingValueContainer<Value> cached = readIfCached(key);

    if (cached != null) {
      cached.removeAssociatedValue(inputId);
      return;
    }

    myMap.remove(key);
  }

  private void updateSingleValueDirectly(Key key, int inputId, Value newValue) throws IOException {
    assert myKeyIsUniqueForIndexedFile;

    ChangeTrackingValueContainer<Value> cached = readIfCached(key);

    if (cached != null) {
      cached.removeAssociatedValue(inputId);
      cached.addValue(inputId, newValue);
      return;
    }

    // do not pollute the cache with keys unique to indexed file
    UpdatableValueContainer<Value> valueContainer = ValueContainerImpl.createNewValueContainer();
    valueContainer.addValue(inputId, newValue);
    myMap.put(key, valueContainer);
  }

  private void putSingleValueDirectly(Key key, int inputId, Value value) throws IOException {
    assert myKeyIsUniqueForIndexedFile;

    ChangeTrackingValueContainer<Value> cached = readIfCached(key);

    if (cached != null) {
      cached.addValue(inputId, value);
      return;
    }

    // do not pollute the cache with keys unique to indexed file
    UpdatableValueContainer<Value> valueContainer = ValueContainerImpl.createNewValueContainer();
    valueContainer.addValue(inputId, value);
    myMap.put(key, valueContainer);
  }

  private @Nullable ChangeTrackingValueContainer<Value> readIfCached(Key key) {
    return myCache.readIfCached(key);
  }

  private static void assertKeyInputIdConsistency(@NotNull Object key, int inputId) {
    assert ((Integer)key).intValue() == inputId;
  }

  @Override
  public void clearCaches() {
    for (ChangeTrackingValueContainer<Value> container : myCache.getCachedValues()) {
      container.dropMergedData();
    }
  }

  @Internal
  @Override
  public final void invalidateCachedMappings() {
    myCache.invalidateAll();
  }

  protected static <T> T unwrapCauseAndRethrow(RuntimeException e) throws StorageException {
    final Throwable cause = e.getCause();
    if (cause instanceof IOException) {
      throw new StorageException(cause);
    }
    if (cause instanceof StorageException) {
      throw (StorageException)cause;
    }
    throw e;
  }

  @TestOnly
  public boolean processKeys(@NotNull Processor<? super Key> processor) throws StorageException {
    try {
      invalidateCachedMappings(); // this will ensure that all new keys are made into the map
      return doProcessKeys(processor);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      unwrapCauseAndRethrow(e);
      return false;
    }
  }

  protected boolean doProcessKeys(@NotNull Processor<? super Key> processor) throws IOException {
    return myMap.processKeys(processor);
  }

  @TestOnly
  public PersistentMapBase<Key, UpdatableValueContainer<Value>> getIndexMap() {
    return myMap.getStorageMap();
  }

  public static @NotNull Path getIndexStorageFile(@NotNull Path baseFile) {
    return baseFile.resolveSibling(baseFile.getFileName() + ".storage");
  }
}
