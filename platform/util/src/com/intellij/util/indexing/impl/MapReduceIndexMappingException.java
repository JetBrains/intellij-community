// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.DataIndexer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An exception thrown by implementations of the {@link DataIndexer#map(Object)}.
 * It carries additional information on a {@link #getClassToBlame() class to blame},
 * which is used to identify the origin plugin throwing an exception.
 */
@ApiStatus.Experimental
public final class MapReduceIndexMappingException extends RuntimeException {
  private final Class<?> myClassToBlame;

  public MapReduceIndexMappingException(@NotNull Throwable cause, @Nullable Class<?> classToBlame) {
    super(cause);
    myClassToBlame = classToBlame;
  }

  public @Nullable Class<?> getClassToBlame() {
    return myClassToBlame;
  }
}
