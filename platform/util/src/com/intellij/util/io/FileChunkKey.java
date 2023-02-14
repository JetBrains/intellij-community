/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

class FileChunkKey<OwnerType> implements Comparable<FileChunkKey<OwnerType>> {
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
  public int compareTo(@NotNull final FileChunkKey<OwnerType> o) {
    if (owner != o.owner) {
      return owner.hashCode() - o.owner.hashCode();
    }
    return offset == o.offset ? 0 : offset - o.offset < 0 ? -1 : 1;
  }
}
