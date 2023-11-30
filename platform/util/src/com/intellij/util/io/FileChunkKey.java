// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

final class FileChunkKey<OwnerType> implements Comparable<FileChunkKey<OwnerType>> {
  private final OwnerType owner;
  private final long offset;

  FileChunkKey(OwnerType owner, long offset) {
    this.owner = owner;
    this.offset = offset;
  }

  @Override
  public int hashCode() {
    return (int)(owner.hashCode() * 31 + offset);
  }

  public OwnerType getOwner() {
    return owner;
  }

  public long getOffset() {
    return offset;
  }

  @Override
  public boolean equals(final Object obj) {
    if (getClass() != obj.getClass()) return false;
    FileChunkKey<OwnerType> k = (FileChunkKey<OwnerType>)obj;
    return k.owner == owner && k.offset == offset;
  }

  @Override
  public int compareTo(final @NotNull FileChunkKey<OwnerType> o) {
    if (owner != o.owner) {
      return owner.hashCode() - o.owner.hashCode();
    }
    return offset == o.offset ? 0 : offset - o.offset < 0 ? -1 : 1;
  }
}
