// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.ByteOrder.nativeOrder;

/// Encodes persistent metadata for a durable map in [StoreMetadata]
final class DurableMapMetadata {
  private static final int STORE_METADATA_VERSION = 1;
  private static final int PAYLOAD_SIZE = Integer.BYTES * 3;

  private final int lookupImplementationId;
  private final int lookupFormatVersion;
  private final int lookupGeneration;

  private DurableMapMetadata(int lookupImplementationId, int lookupFormatVersion, int lookupGeneration) {
    this.lookupImplementationId = lookupImplementationId;
    this.lookupFormatVersion = lookupFormatVersion;
    this.lookupGeneration = lookupGeneration;
  }

  static @NotNull DurableMapMetadata initial() {
    return new DurableMapMetadata(
      ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID,
      ExtendibleHashMapInt32ToInt64.IMPLEMENTATION_VERSION,
      ExtendibleHashMapStorageOverLookupBlocks.INITIAL_GENERATION
    );
  }

  static @NotNull DurableMapMetadata fromStoreMetadata(@NotNull StoreMetadata storeMetadata) throws IOException {
    if (storeMetadata.version() != STORE_METADATA_VERSION) {
      throw new UnsupportedFormatException(
        "durable map store metadata", STORE_METADATA_VERSION, storeMetadata.version()
      );
    }
    if (storeMetadata.size() != PAYLOAD_SIZE) {
      throw new CorruptedException(
        "Durable map store metadata size(=" + storeMetadata.size() + ") must be " + PAYLOAD_SIZE
      );
    }
    var buffer = storeMetadata.asReadOnlyBuffer().order(nativeOrder());
    var lookupImplementationId = buffer.getInt();
    var lookupFormatVersion = buffer.getInt();
    var lookupGeneration = buffer.getInt();
    if (lookupImplementationId <= 0) {
      throw new CorruptedException("lookupImplementationId must be positive");
    }
    if (lookupFormatVersion <= 0) {
      throw new CorruptedException("lookupFormatVersion must be positive");
    }
    if (lookupGeneration < 0) {
      throw new CorruptedException("lookupGeneration must not be negative");
    }
    return new DurableMapMetadata(lookupImplementationId, lookupFormatVersion, lookupGeneration);
  }

  @NotNull StoreMetadata toStoreMetadata() {
    var bytes = ByteBuffer.allocate(PAYLOAD_SIZE)
      .order(nativeOrder())
      .putInt(lookupImplementationId)
      .putInt(lookupFormatVersion)
      .putInt(lookupGeneration)
      .array();
    return StoreMetadata.copyOf(STORE_METADATA_VERSION, bytes);
  }

  int lookupImplementationId() {
    return lookupImplementationId;
  }

  int lookupFormatVersion() {
    return lookupFormatVersion;
  }

  int lookupGeneration() {
    return lookupGeneration;
  }

  @Override
  public String toString() {
    return "DurableMapMetadata[lookup={implId=" + lookupImplementationId +
           ", version=" + lookupFormatVersion +
           ", gen=" + lookupGeneration + "}]";
  }
}
