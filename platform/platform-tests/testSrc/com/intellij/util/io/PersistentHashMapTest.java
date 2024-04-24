// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.KeyValueStoreTestBase;
import org.jetbrains.annotations.NotNull;

public class PersistentHashMapTest extends KeyValueStoreTestBase<String, String, PersistentHashMap<String, String>> {
  public PersistentHashMapTest() { super(STRING_SUBSTRATE_DECODER); }

  @Override
  protected @NotNull StorageFactory<PersistentHashMap<String, String>> factory() {
    return storagePath -> new PersistentHashMap<>(
      storagePath,
      EnumeratorStringDescriptor.INSTANCE,
      EnumeratorStringDescriptor.INSTANCE,
      1024
    );
  }
}
