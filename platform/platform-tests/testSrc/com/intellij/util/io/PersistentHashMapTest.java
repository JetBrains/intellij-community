// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.dev.StorageFactory;
import org.jetbrains.annotations.NotNull;

public class PersistentHashMapTest extends KeyValueStoreTestBase<PersistentHashMap<String, String>> {
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
