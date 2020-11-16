// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

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
public final class PersistentHashMapBuilder<Key, Value> {
  @NotNull private final Path myFile;
  @NotNull private final KeyDescriptor<Key> myKeyDescriptor;
  @NotNull private final DataExternalizer<Value> myValueExternalizer;

  private Integer myInitialSize = null;
  private Integer myVersion = null;
  private StorageLockContext myLockContext = null;
  private Boolean myInlineValues = null;
  private Boolean myIsReadOnly = null;

  private PersistentHashMapBuilder(@NotNull Path file,
                                   @NotNull KeyDescriptor<Key> keyDescriptor,
                                   @NotNull DataExternalizer<Value> valueExternalizer) {
    myFile = file;
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
  }

  @NotNull
  public PersistentHashMap<Key, Value> build() throws IOException {
    return new PersistentHashMap<>(this, false);
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
  public static <Key, Value> PersistentHashMapBuilder<Key, Value> newBuilder(@NotNull Path file,
                                                                             @NotNull KeyDescriptor<Key> keyDescriptor,
                                                                             @NotNull DataExternalizer<Value> valueExternalizer) {
    return new PersistentHashMapBuilder<>(file, keyDescriptor, valueExternalizer);
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> withInitialSize(int initialSize) {
    myInitialSize = initialSize;
    return this;
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> withVersion(int version) {
    myVersion = version;
    return this;
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> withReadonly(boolean readonly) {
    myIsReadOnly = readonly;
    return this;
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> readonly() {
    return withReadonly(true);
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> inlineValues(boolean inlineValues) {
    if (inlineValues && !(myValueExternalizer instanceof IntInlineKeyDescriptor)) {
      throw new IllegalStateException("can't inline values for externalizer " + myValueExternalizer.getClass());
    }
    myInlineValues = inlineValues;
    return this;
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> inlineValues() {
    return inlineValues(true);
  }

  @NotNull
  public PersistentHashMapBuilder<Key, Value> withStorageLockContext(@Nullable StorageLockContext context) {
    myLockContext = context;
    return this;
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

  @Nullable
  public StorageLockContext getLockContext() {
    return myLockContext;
  }
}
