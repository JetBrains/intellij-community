// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl.layout;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.nio.ByteOrder.nativeOrder;

@ApiStatus.Internal
public final class LayoutUtils {
  private LayoutUtils() { }


  //@formatter:off
  public static final ValueLayout.OfByte  INT8_LAYOUT            = ValueLayout.JAVA_BYTE;
  public static final ValueLayout.OfShort INT16_LAYOUT           = ValueLayout.JAVA_SHORT.withOrder(nativeOrder());
  public static final ValueLayout.OfInt   INT32_LAYOUT           = ValueLayout.JAVA_INT.withOrder(nativeOrder());
  public static final ValueLayout.OfLong  INT64_LAYOUT           = ValueLayout.JAVA_LONG.withOrder(nativeOrder());
  public static final ValueLayout.OfLong  INT64_UNALIGNED_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(nativeOrder());
  //@formatter:on


  public static @NotNull VarHandle fieldHandle(@NotNull MemoryLayout layout,
                                               @NotNull MemoryLayout.PathElement field) {
    return layout.varHandle(field).withInvokeExactBehavior();
  }

  public static @NotNull VarHandle fieldHandle(@NotNull MemoryLayout layout,
                                               @NotNull String fieldName) {
    return fieldHandle(layout, groupElement(fieldName));
  }
}
