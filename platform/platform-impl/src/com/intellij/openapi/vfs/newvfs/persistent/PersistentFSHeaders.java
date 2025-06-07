// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@ApiStatus.Internal
public final class PersistentFSHeaders {
  //@formatter:off
  static final int HEADER_VERSION_OFFSET                  =  0;  // int32
  static final int HEADER_RESERVED_OFFSET_1               =  4;  // int32
  static final int HEADER_GLOBAL_MOD_COUNT_OFFSET         =  8;  // int32
  @VisibleForTesting
  public static final int HEADER_CONNECTION_STATUS_OFFSET = 12;  // int32
  static final int HEADER_TIMESTAMP_OFFSET                = 16;  // int64
  static final int HEADER_ERRORS_ACCUMULATED_OFFSET       = 24;  // int32
  static final int HEADER_FLAGS_OFFSET                    = 28;  // int32

  //reserve a few bytes of header for the generations to come
  //Header size must be int64-aligned, so records start on int64-aligned offset
  static final int HEADER_SIZE                            = 40;
  //@formatter:on

  @MagicConstant(flagsFromClass = PersistentFSHeaders.class)
  @Target(ElementType.TYPE_USE)
  public @interface HeaderOffset {
  }


  /** FLAGS header field values: */
  public static final class Flags {

    //@formatter:off

    /** Current implementation of 'defragmentation' is really a 'drop VFS and rebuild from scratch' */
    @ApiStatus.Internal
    public static final int FLAGS_DEFRAGMENTATION_REQUESTED    = 1;

    //@formatter:on
  }


}
