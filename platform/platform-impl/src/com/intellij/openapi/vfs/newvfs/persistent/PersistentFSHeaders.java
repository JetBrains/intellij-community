// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@ApiStatus.Internal
public final class PersistentFSHeaders {
  //@formatter:off
  static final int HEADER_VERSION_OFFSET                  =  0;  // int32
  static final int HEADER_RESERVED_OFFSET_1               =  4;  // int32
  static final int HEADER_GLOBAL_MOD_COUNT_OFFSET         =  8;  // int32
  public static final int HEADER_CONNECTION_STATUS_OFFSET        = 12;  // int32
  static final int HEADER_TIMESTAMP_OFFSET                = 16;  // int64
  static final int HEADER_ERRORS_ACCUMULATED_OFFSET       = 24;  // int32

  //reserve 3 int32 header fields for the generations to come
  //Header size must be int64-aligned, so records start on int64-aligned offset
  static final int HEADER_SIZE                            = 40;
  //@formatter:on


  //CONNECTION_STATUS header field values:
  //@formatter:off
  public static final int IN_USE_STAMP         = 0x12ad34e4;
  static final int SAFELY_CLOSED_STAMP  = 0;
  //@formatter:on

  @MagicConstant(flagsFromClass = PersistentFSHeaders.class)
  @Target(ElementType.TYPE_USE)
  public @interface HeaderOffset {
  }
}
