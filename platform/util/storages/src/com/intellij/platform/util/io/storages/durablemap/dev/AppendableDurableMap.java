// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap.dev;

import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.io.AppendablePersistentMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

/**
 * Analog {@link AppendablePersistentMap}, but with different API design: map explicitly states
 * that value is a container (set) of items, and that items could be appended to the container
 * in a more optimized way then just {@code values=get(key); put(key, values+newItem)}
 */
public interface AppendableDurableMap<K, VItem> extends DurableMap<K, Set<VItem>> {

  @Nullable
  Items<VItem> items(@NotNull K key) throws IOException;

  interface Items<VItem> {

    void append(@NotNull VItem item) throws IOException;

    void remove(@NotNull VItem item) throws IOException;

    <E extends Throwable> boolean forEach(@NotNull ThrowableConsumer<? super VItem, E> consumer) throws IOException, E;
  }
}
