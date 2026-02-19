// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents remapping of {@code inputId}-s stored in {@link ValueContainerImpl} to fileIds.
 *
 * <p>Usually it is just an identity mapping and {@code inputId == fileId}.
 * But sometimes it can be {@code hashId -> many fileId-s},
 * when multiple files have the same content hash.
 */
@ApiStatus.Internal
@FunctionalInterface
public interface ValueContainerInputRemapping {
  ValueContainerInputRemapping IDENTITY = inputId -> new int[]{inputId};

  // one of: int or int[]. Object is being used here to avoid additional allocations
  @NotNull Object remap(int inputId);
}
