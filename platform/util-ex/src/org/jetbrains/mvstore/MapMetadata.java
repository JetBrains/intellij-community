// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.mvstore;

import io.netty.buffer.ByteBuf;
import org.jetbrains.integratedBinaryPacking.IntBitPacker;
import org.jetbrains.integratedBinaryPacking.LongBitPacker;
import org.jetbrains.mvstore.type.DataType;

final class MapMetadata {
  final int id;
  final long createVersion;

  MapMetadata(int id, long createVersion) {
    this.id = id;
    this.createVersion = createVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MapMetadata value = (MapMetadata)o;
    return id == value.id && createVersion == value.createVersion;
  }

  @Override
  public int hashCode() {
    return 31 * id + (int)(createVersion ^ (createVersion >>> 32));
  }

  @Override
  public String toString() {
    return "MapMetadata(" +
           "id=" + id +
           ", createVersion=" + createVersion +
           ')';
  }

  static final class MapMetadataSerializer implements DataType<MapMetadata> {
    @Override
    public MapMetadata[] createStorage(int size) {
      return new MapMetadata[size];
    }

    @Override
    public int getMemory(MapMetadata obj) {
      return getFixedMemory();
    }

    @Override
    public int getFixedMemory() {
      return DataUtil.VAR_INT_MAX_SIZE + DataUtil.VAR_LONG_MAX_SIZE;
    }

    @Override
    public void write(ByteBuf buf, MapMetadata obj) {
      IntBitPacker.writeVar(buf, obj.id);
      LongBitPacker.writeVar(buf, obj.createVersion);
    }

    @Override
    public MapMetadata read(ByteBuf buf) {
      return new MapMetadata(IntBitPacker.readVar(buf), LongBitPacker.readVar(buf));
    }
  }
}
