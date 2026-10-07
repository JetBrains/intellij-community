// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.storages.durablemap;

import com.intellij.platform.util.io.storages.UnsupportedFormatException;
import com.intellij.platform.util.io.storages.database.DurableMapImplementation;
import com.intellij.platform.util.io.storages.database.spi.StoreMetadata;
import com.intellij.platform.util.io.storages.database.storages.extendiblehashmap.ExtendibleHashMapStorageOverLookupBlocks;
import com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64;
import com.intellij.util.io.CorruptedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_SEPARATELY;
import static com.intellij.platform.util.io.storages.database.DurableMapImplementation.KEY_VALUE_TOGETHER;
import static java.nio.ByteOrder.nativeOrder;

/// Encodes persistent metadata for a durable map in [StoreMetadata].
@ApiStatus.Internal
public final class DurableMapMetadata {
  private static final int STORE_METADATA_VERSION = 1;
  private static final int PAYLOAD_SIZE = Integer.BYTES * 5;

  /// persistent id for [DurableMapImplementation#KEY_VALUE_TOGETHER]
  private static final int KEY_VALUE_TOGETHER_ID = 1;
  /// persistent id for [DurableMapImplementation#KEY_VALUE_SEPARATELY]
  private static final int KEY_VALUE_SEPARATELY_ID = 2;

  private final @NotNull DurableMapImplementation mapImplementation;
  private final int mapFormatVersion;
  private final int lookupImplementationId;
  private final int lookupFormatVersion;
  private final int lookupGeneration;

  private DurableMapMetadata(@NotNull DurableMapImplementation mapImplementation,
                             int mapFormatVersion,
                             int lookupImplementationId,
                             int lookupFormatVersion,
                             int lookupGeneration) {
    this.mapImplementation = mapImplementation;
    this.mapFormatVersion = mapFormatVersion;
    this.lookupImplementationId = lookupImplementationId;
    this.lookupFormatVersion = lookupFormatVersion;
    this.lookupGeneration = lookupGeneration;
  }

  static @NotNull DurableMapMetadata initial(@NotNull DurableMapImplementation mapImplementation) {
    var mapFormatVersion = switch (mapImplementation) {
      case KEY_VALUE_TOGETHER -> DurableMapOverBlocks.IMPLEMENTATION_VERSION;
      case KEY_VALUE_SEPARATELY -> DurableMapWithKeyCatalogOverBlocks.IMPLEMENTATION_VERSION;
    };
    return new DurableMapMetadata(
      mapImplementation,
      mapFormatVersion,
      ExtendibleHashMapStorageOverLookupBlocks.IMPLEMENTATION_ID,
      ExtendibleHashMapInt32ToInt64.IMPLEMENTATION_VERSION,
      ExtendibleHashMapStorageOverLookupBlocks.INITIAL_GENERATION
    );
  }

  public static @NotNull DurableMapMetadata fromStoreMetadata(@NotNull StoreMetadata storeMetadata) throws IOException {
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
    var mapImplementation = mapImplementationById(buffer.getInt());
    var mapFormatVersion = buffer.getInt();
    var lookupImplementationId = buffer.getInt();
    var lookupFormatVersion = buffer.getInt();
    var lookupGeneration = buffer.getInt();
    if (mapFormatVersion <= 0) {
      throw new CorruptedException("mapFormatVersion must be positive");
    }
    validateLookup(lookupImplementationId, lookupFormatVersion, lookupGeneration);
    return new DurableMapMetadata(
      mapImplementation,
      mapFormatVersion,
      lookupImplementationId,
      lookupFormatVersion,
      lookupGeneration
    );
  }

  private static void validateLookup(int lookupImplementationId, int lookupFormatVersion, int lookupGeneration) throws CorruptedException {
    if (lookupImplementationId <= 0) {
      throw new CorruptedException("lookupImplementationId must be positive");
    }
    if (lookupFormatVersion <= 0) {
      throw new CorruptedException("lookupFormatVersion must be positive");
    }
    if (lookupGeneration < 0) {
      throw new CorruptedException("lookupGeneration must not be negative");
    }
  }

  private static @NotNull DurableMapImplementation mapImplementationById(int implementationId) throws CorruptedException {
    return switch (implementationId) {
      case KEY_VALUE_TOGETHER_ID -> KEY_VALUE_TOGETHER;
      case KEY_VALUE_SEPARATELY_ID -> KEY_VALUE_SEPARATELY;
      default -> throw new CorruptedException("Unknown durable map implementation " + implementationId);
    };
  }

  private static int mapImplementationId(@NotNull DurableMapImplementation implementation) {
    return switch (implementation) {
      case KEY_VALUE_TOGETHER -> KEY_VALUE_TOGETHER_ID;
      case KEY_VALUE_SEPARATELY -> KEY_VALUE_SEPARATELY_ID;
    };
  }

  @NotNull StoreMetadata toStoreMetadata() {
    var bytes = ByteBuffer.allocate(PAYLOAD_SIZE)
      .order(nativeOrder())
      .putInt(mapImplementationId(mapImplementation))
      .putInt(mapFormatVersion)
      .putInt(lookupImplementationId)
      .putInt(lookupFormatVersion)
      .putInt(lookupGeneration)
      .array();
    return StoreMetadata.copyOf(STORE_METADATA_VERSION, bytes);
  }

  public @NotNull DurableMapImplementation mapImplementation() {
    return mapImplementation;
  }

  int mapFormatVersion() {
    return mapFormatVersion;
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
    return "DurableMapMetadata[implementation=" + mapImplementation +
           ", mapVersion=" + mapFormatVersion +
           ", lookup={implId=" + lookupImplementationId +
           ", version=" + lookupFormatVersion +
           ", gen=" + lookupGeneration + "}]";
  }
}
