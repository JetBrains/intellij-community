// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages;

import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.ValueLayout;

import static java.nio.ByteOrder.nativeOrder;

/// Defines unaligned native-order layouts for persistent serializer formats.
@ApiStatus.Internal
public final class PersistentFormatLayouts {
  public static final ValueLayout.OfShort JAVA_SHORT_UNALIGNED = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(nativeOrder());
  public static final ValueLayout.OfChar JAVA_CHAR_UNALIGNED = ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(nativeOrder());
  public static final ValueLayout.OfInt JAVA_INT_UNALIGNED = ValueLayout.JAVA_INT_UNALIGNED.withOrder(nativeOrder());
  public static final ValueLayout.OfLong JAVA_LONG_UNALIGNED = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(nativeOrder());

  private PersistentFormatLayouts() { }
}
