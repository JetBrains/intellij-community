// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.Processor;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.util.Collection;

/**
 * A Base interface for custom PersistentHashMap implementations
 */
@ApiStatus.Experimental
public interface PersistentHashMapBase<Key, Value> extends AppendablePersistentMap<Key, Value> {
  void dropMemoryCaches();

  @NotNull
  Collection<Key> getAllKeysWithExistingMapping() throws IOException;

  boolean processKeysWithExistingMapping(@NotNull Processor<? super Key> processor) throws IOException;
}
