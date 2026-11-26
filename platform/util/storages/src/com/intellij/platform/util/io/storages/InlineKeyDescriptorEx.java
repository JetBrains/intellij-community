// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import com.intellij.util.io.InlineKeyPolicy;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents keys by stable, reversible int codes so a lookup can supply keys without reading their records.
 * The hash must equal {@link #toInt}, and keys must be equal exactly when their codes are equal.
 * Serialization can use a different representation.
 */
@ApiStatus.Internal
public interface InlineKeyDescriptorEx<K> extends KeyDescriptorEx<K>, InlineKeyPolicy<K> {
  /** The code must remain the same after the storage reopens */
  @Override
  int toInt(K key);

  /** The result must have the specified code and equal every key with that code */
  @Override
  K fromInt(int code);

  //KeyDescriptorEx re-declares these methods as abstract, so Java requires explicit overrides to pick the
  // InlineKeyPolicy defaults:
  @Override
  default int getHashCode(K value) {
    return InlineKeyPolicy.super.getHashCode(value);
  }

  @Override
  default boolean isEqual(K key1, K key2) {
    return InlineKeyPolicy.super.isEqual(key1, key2);
  }

}
