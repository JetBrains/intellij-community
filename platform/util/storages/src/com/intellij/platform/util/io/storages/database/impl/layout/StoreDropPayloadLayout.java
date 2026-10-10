// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.INT32_LAYOUT;
import static com.intellij.platform.util.io.storages.database.impl.layout.LayoutUtils.fieldHandle;

/// ```
/// StoreDropPayloadLayout {
///   storeId: int32
/// }
/// ```
@ApiStatus.Internal
public final class StoreDropPayloadLayout {
  public static final short PAYLOAD_FORMAT_VERSION = 1;

  public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
    INT32_LAYOUT.withName("storeId")
  ).withName("StoreDropPayloadLayout");

  public static final VarHandle STORE_ID_HANDLE = fieldHandle(LAYOUT, "storeId");

  public static final int PAYLOAD_SIZE = Math.toIntExact(LAYOUT.byteSize());

  private StoreDropPayloadLayout() { }

  /// Contains the identifier of a dropped store.
  public record StoreDropRecordPayload(int storeId) {
    public static @NotNull StoreDropRecordPayload read(@NotNull MemorySegment source) {
      return new StoreDropRecordPayload((int)STORE_ID_HANDLE.get(source, 0L));
    }

    public void writeTo(@NotNull MemorySegment target) {
      STORE_ID_HANDLE.set(target, 0L, storeId);
    }
  }
}
