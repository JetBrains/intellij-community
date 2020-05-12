// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Experimental
public interface AppendablePersistentMap<K, V> extends PersistentMap<K, V> {
  void appendData(K key, @NotNull PersistentHashMap.ValueDataAppender appender) throws IOException;
}