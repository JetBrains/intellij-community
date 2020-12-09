// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore.type;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.KeyManager;
import org.jetbrains.mvstore.MVMap;
import org.jetbrains.mvstore.ObjectKeyManager;

public interface KeyableDataType<T> extends DataType<T> {
  /**
    * Compare two keys.
    *
    * @param a the first key
    * @param b the second key
    * @return -1 if the first key is smaller, 1 if larger, and 0 if equal
    * @throws UnsupportedOperationException if the type is not orderable
    */
   int compare(T a, T b);

    default @NotNull KeyManager<T> createEmptyManager(@NotNull MVMap<T, ?> map) {
      //noinspection unchecked
      return (KeyManager<T>)ObjectKeyManager.EMPTY;
    }

    default @NotNull KeyManager<T> createManager(ByteBuf buf, int count) {
      return new ObjectKeyManager<>(this, buf, count);
    }
}
