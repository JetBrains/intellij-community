// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;

/**
 * Represents remapping of inputIds from {@link ValueContainerImpl} to a file ids.
 *
 * <p>Usually it is just an identity mapping and inputId == fileId. But sometimes it can be hashIs -> many fileIds.
 */
@ApiStatus.Internal
@FunctionalInterface
public interface ValueContainerInputRemapping {
  ValueContainerInputRemapping IDENTITY = inputId -> new int[]{inputId};

  int[] remap(int inputId);
}
