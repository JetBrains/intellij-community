// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PersistentFSHeaders {
  /** FLAGS header field values: */
  public static final class Flags {

    //@formatter:off

    /** Current implementation of 'defragmentation' is really a 'drop VFS and rebuild from scratch' */
    @ApiStatus.Internal
    public static final int FLAGS_DEFRAGMENTATION_REQUESTED    = 1;

    /**
     * If this flag is set -- VFS was NOT properly closed at some point of its lifetime (including the very last time).
     * This property is <b>'sticky'</b> (contrary to {@link PersistentFSRecordsStorage#wasClosedProperly()}): once set, it
     * is never reset. I.e., once VFS wasn't closed properly once, VFS always remains under some level of suspicion.
     */
    @ApiStatus.Internal
    public static final int FLAGS_WAS_NOT_PROPERLY_CLOSED_ONCE = 2;

    //@formatter:on
  }
}
