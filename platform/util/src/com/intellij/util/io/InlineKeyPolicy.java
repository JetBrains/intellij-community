// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.ApiStatus;

/// Equality policy for keys that are represented by unique int codes: keys are equal exactly when their codes are equal.
/// The common base of the legacy [InlineKeyDescriptor] and of `InlineKeyDescriptorEx` from `intellij.platform.util.io.storages`.
/// It lets the code in `platform.util` use int-specialized structures for both descriptors, e.g.
/// [com.intellij.util.indexing.impl.SlruIndexStorageCacheProvider].
///
/// Contract: `toInt(fromInt(code)) == code`, and [#getHashCode]/[#isEqual] must agree with the codes, because a caller may
/// replace them with a comparison of codes.
@ApiStatus.Internal
public interface InlineKeyPolicy<K> extends EqualityPolicy<K> {
  int toInt(K key);

  K fromInt(int code);

  @Override
  default int getHashCode(K value) {
    return toInt(value);
  }

  @Override
  default boolean isEqual(K key1, K key2) {
    return toInt(key1) == toInt(key2);
  }
}
