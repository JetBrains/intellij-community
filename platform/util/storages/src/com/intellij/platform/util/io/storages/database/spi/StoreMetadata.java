// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.spi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/// Immutable versioned metadata of one store
@ApiStatus.Internal
public final class StoreMetadata {
  public static final @NotNull StoreMetadata EMPTY = new StoreMetadata(0, new byte[0]);

  /// version of binary format in [#bytes]
  private final int version;
  private final byte @NotNull [] bytes;

  private StoreMetadata(int version, byte @NotNull [] bytes) {
    if (version < 0) {
      throw new IllegalArgumentException("version(=" + version + ") must not be negative");
    }
    if (version == 0 && bytes.length != 0) {
      throw new IllegalArgumentException("Store metadata with version zero must be empty");
    }
    this.version = version;
    this.bytes = bytes;
  }

  /// Creates metadata that owns a copy of the specified bytes
  public static @NotNull StoreMetadata copyOf(int version, byte @NotNull [] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    if (version == 0 && bytes.length == 0) {
      return EMPTY;
    }
    return new StoreMetadata(version, bytes.clone());
  }

  /// Creates metadata that owns a copy of the remaining bytes without changing the source position
  public static @NotNull StoreMetadata copyOf(int version, @NotNull ByteBuffer source) {
    Objects.requireNonNull(source, "source");
    if (version == 0 && !source.hasRemaining()) {
      return EMPTY;
    }
    var bytes = new byte[source.remaining()];
    source.slice().get(bytes);
    return new StoreMetadata(version, bytes);
  }

  public int version() {
    return version;
  }

  public int size() {
    return bytes.length;
  }

  /// @return a new read-only view of the metadata bytes
  public @NotNull ByteBuffer asReadOnlyBuffer() {
    return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
  }

  @Override
  public boolean equals(Object object) {
    return this == object ||
           object instanceof StoreMetadata other &&
           version == other.version &&
           Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return 31 * Integer.hashCode(version) + Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "StoreMetadata[version=" + version + ", size=" + bytes.length + "]";
  }
}
