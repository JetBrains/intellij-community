// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * A builder helper for {@link PersistentHashMap}
 *
 * @see PersistentHashMap
 */
@ApiStatus.Experimental
public final class PersistentMapBuilder<Key, Value> {
  private final @NotNull Path myFile;
  private final @NotNull KeyDescriptor<Key> myKeyDescriptor;
  private final @NotNull DataExternalizer<Value> myValueExternalizer;

  private Integer myInitialSize;
  private Integer myVersion;
  private StorageLockContext myLockContext;
  private Boolean myInlineValues;
  private Boolean myIsReadOnly;
  private Boolean myHasChunks;
  private Boolean myCompactOnClose;
  private @NotNull ExecutorService myWalExecutor;
  private boolean myEnableWal;

  private PersistentMapBuilder(final @NotNull Path file,
                               final @NotNull KeyDescriptor<Key> keyDescriptor,
                               final @NotNull DataExternalizer<Value> valueExternalizer,
                               final Integer initialSize,
                               final Integer version,
                               final StorageLockContext lockContext,
                               final Boolean inlineValues,
                               final Boolean isReadOnly,
                               final Boolean hasChunks,
                               final Boolean compactOnClose,
                               final @NotNull ExecutorService walExecutor,
                               final boolean enableWal) {
    myFile = file;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    myInitialSize = initialSize;
    myVersion = version;
    myLockContext = lockContext;
    myInlineValues = inlineValues;
    myIsReadOnly = isReadOnly;
    myHasChunks = hasChunks;
    myCompactOnClose = compactOnClose;
    myWalExecutor = walExecutor;
    myEnableWal = enableWal;
  }

  private PersistentMapBuilder(@NotNull Path file,
                               @NotNull KeyDescriptor<Key> keyDescriptor,
                               @NotNull DataExternalizer<Value> valueExternalizer) {
    this(file, keyDescriptor, valueExternalizer,
         null, null, null, null, null, null, null,
         ConcurrencyUtil.newSameThreadExecutorService(),
         false);
  }

  @NotNull
  public PersistentHashMap<Key, Value> build() throws IOException {
    return new PersistentHashMap<>(buildImplementation());
  }

  @NotNull
  public PersistentMapBase<Key, Value> buildImplementation() throws IOException {
    Boolean oldHasNoChunksValue = null;
    if (myHasChunks != null) {
      oldHasNoChunksValue = PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.get();
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(!myHasChunks);
    }
    Boolean previousReadOnly = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get();
    PersistentHashMapValueStorage.CreationTimeOptions.READONLY.set(myIsReadOnly);

    try {
      if (SystemProperties.getBooleanProperty("idea.use.in.memory.persistent.map", false)) {
        return new PersistentMapInMemory<>(this);
      }

      return new PersistentMapImpl<>(this);
    }
    finally {
      if (myHasChunks != null) {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(oldHasNoChunksValue);
      }
      PersistentHashMapValueStorage.CreationTimeOptions.READONLY.set(previousReadOnly);
    }
  }

  @NotNull
  public Path getFile() {
    return myFile;
  }

  @NotNull
  public KeyDescriptor<Key> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  public DataExternalizer<Value> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  public static <Key, Value> PersistentMapBuilder<Key, Value> newBuilder(@NotNull Path file,
                                                                         @NotNull KeyDescriptor<Key> keyDescriptor,
                                                                         @NotNull DataExternalizer<Value> valueExternalizer) {
    return new PersistentMapBuilder<>(file, keyDescriptor, valueExternalizer);
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withInitialSize(int initialSize) {
    myInitialSize = initialSize;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withVersion(int version) {
    myVersion = version;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withReadonly(boolean readonly) {
    myIsReadOnly = readonly;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> readonly() {
    return withReadonly(true);
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withWal(boolean enableWal) {
    myEnableWal = enableWal;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withWalExecutor(@NotNull ExecutorService service) {
    myWalExecutor = service;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> inlineValues(boolean inlineValues) {
    if (inlineValues && !(myValueExternalizer instanceof IntInlineKeyDescriptor)) {
      throw new IllegalStateException("can't inline values for externalizer " + myValueExternalizer.getClass());
    }
    myInlineValues = inlineValues;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> inlineValues() {
    return inlineValues(true);
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withStorageLockContext(@Nullable StorageLockContext context) {
    myLockContext = context;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> hasChunks(boolean hasChunks) {
    myHasChunks = hasChunks;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> hasNoChunks() {
    myHasChunks = false;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> withCompactOnClose(boolean compactOnClose) {
    myCompactOnClose = compactOnClose;
    return this;
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> compactOnClose() {
    return withCompactOnClose(true);
  }

  public int getInitialSize(int defaultValue) {
    if (myInitialSize != null) return myInitialSize;
    return defaultValue;
  }

  public int getVersion(int defaultValue) {
    if (myVersion != null) return myVersion;
    return defaultValue;
  }

  public boolean getInlineValues(boolean defaultValue) {
    if (myInlineValues != null) return myInlineValues;
    return defaultValue;
  }

  public boolean getReadOnly(boolean defaultValue) {
    if (myIsReadOnly != null) return myIsReadOnly;
    return defaultValue;
  }

  public boolean getCompactOnClose(boolean defaultCompactOnClose) {
    if (myCompactOnClose != null) return myCompactOnClose;
    return defaultCompactOnClose;
  }

  public boolean isEnableWal() {
    return myEnableWal;
  }

  @NotNull
  public ExecutorService getWalExecutor() {
    return myWalExecutor;
  }

  @Nullable
  public StorageLockContext getLockContext() {
    return myLockContext;
  }

  /**
   * Since builder is not immutable, it is quite useful to have defensive copy of it
   *
   * @return shallow copy of this builder.
   */
  public PersistentMapBuilder<Key, Value> copy() {
    return new PersistentMapBuilder<>(
      myFile, myKeyDescriptor, myValueExternalizer,
      myInitialSize, myVersion, myLockContext, myInlineValues, myIsReadOnly, myHasChunks, myCompactOnClose,
      myWalExecutor, myEnableWal
    );
  }

  @NotNull
  public PersistentMapBuilder<Key, Value> copyWithFile(final @NotNull Path file) {
    return new PersistentMapBuilder<>(
      file, myKeyDescriptor, myValueExternalizer,
      myInitialSize, myVersion, myLockContext, myInlineValues, myIsReadOnly, myHasChunks, myCompactOnClose,
      myWalExecutor, myEnableWal
    );
  }
}
