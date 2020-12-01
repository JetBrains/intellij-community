// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A builder helper for {@link PersistentHashMap}
 * @see PersistentHashMap
 */
@ApiStatus.Experimental
public final class PersistentMapBuilder<Key, Value> {
  @NotNull private final Path myFile;
  @NotNull private final KeyDescriptor<Key> myKeyDescriptor;
  @NotNull private final DataExternalizer<Value> myValueExternalizer;

  private Integer myInitialSize;
  private Integer myVersion;
  private StorageLockContext myLockContext;
  private Boolean myInlineValues;
  private Boolean myIsReadOnly;
  private Boolean myHasChunks;
  private IOCancellationCallback myCancellationCallback;
  private Boolean myCompactOnClose = null;

  private PersistentMapBuilder(@NotNull Path file,
                               @NotNull KeyDescriptor<Key> keyDescriptor,
                               @NotNull DataExternalizer<Value> valueExternalizer) {
    myFile = file;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
  }

  @NotNull
  public PersistentHashMap<Key, Value> build() throws IOException {
    return new PersistentHashMap<>(buildImplementation());
  }

  @NotNull
  private PersistentMapBase<Key, Value> buildImplementation() throws IOException {
    Boolean oldHasNoChunksValue = null;
    if (myHasChunks != null) {
      oldHasNoChunksValue = PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.get();
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(!myHasChunks);
    }
    Boolean previousReadOnly = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get();
    PersistentHashMapValueStorage.CreationTimeOptions.READONLY.set(myIsReadOnly);

    IOCancellationCallback previousIoCancellationCallback = null;
    if (myCancellationCallback != null) {
      previousIoCancellationCallback = PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.get();
      PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.set(myCancellationCallback);
    }
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

      if (myCancellationCallback != null) {
        PersistentHashMapValueStorage.CreationTimeOptions.EXCEPTIONAL_IO_CANCELLATION.set(previousIoCancellationCallback);
      }
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
  public PersistentMapBuilder<Key, Value> withIoCancellationCallback(@NotNull IOCancellationCallback ioCancellationCallback) {
    myCancellationCallback = ioCancellationCallback;
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

  @Nullable
  public StorageLockContext getLockContext() {
    return myLockContext;
  }
}
