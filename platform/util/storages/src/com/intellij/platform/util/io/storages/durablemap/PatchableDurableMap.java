// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.platform.util.io.storages.DataExternalizerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// A durable map with additional capability: values could be appended (=modified) with patches ([patchValue]), instead
///  of full replacement ([put]).
@ApiStatus.Internal
public interface PatchableDurableMap<K, V, P> extends DurableMap<K, V> {

  /// Applies `patch` to the value for `key`, or to an empty value if the key is absent;
  /// The operation is atomic with respect to other operations on this map;
  /// Patch(es) may be applied immediately, or stored as a patch, and applied later, during value reconstruction
  /// in [get] -- all this is up to implementation;
  /// >1 patches could be applied to a single value -- those patches apply in order;
  void patchValue(@NotNull K key, @NotNull P patch) throws IOException;


  /// Extend [[DataExternalizerEx] to work with [[PatchableDurableMap].
  ///
  /// [PatchableDurableMap] requires both value [V] and patches-to-value [P] to be encoded in a common format,
  ///  so [#read(java.nio.ByteBuffer)] is able to consume an initial value followed by {0..N} patches, and return
  ///  their combined value.
  /// Patches without an initial value apply to an empty value -- whatever empty value is should be defined by this
  ///  externalizer impl.
  @ApiStatus.Internal
  interface PatchableValueExternalizer<V, P> extends DataExternalizerEx<V> {
    /// Encodes `patch`, without loading the value to which it applies.
    /// The patch format must be recognizable by [#read], together with value [V] format
    @NotNull KnownSizeRecordWriter writerForPatch(@NotNull P patch) throws IOException;
  }
}
