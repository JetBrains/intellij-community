// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * FIXME type something meaningful here
 */
public class VFSNeedsRebuildException extends IOException {
  private final @NotNull RebuildCause rebuildCause;

  public VFSNeedsRebuildException(@NotNull RebuildCause rebuildCause,
                                  @NotNull String message) {
    super(message);
    this.rebuildCause = rebuildCause;
  }

  public VFSNeedsRebuildException(@NotNull RebuildCause rebuildCause,
                                  @NotNull String message,
                                  @NotNull Throwable cause) {
    super(message, cause);
    this.rebuildCause = rebuildCause;
  }

  public RebuildCause rebuildCause() {
    return rebuildCause;
  }

  public enum RebuildCause {
    NONE,

    /** No VFS, build empty from scratch */
    INITIAL,

    /** Pieces of VFS data are inconsistent with each other (i.e. corrupted) */
    DATA_INCONSISTENT,
    SCHEDULED_REBUILD,
    NOT_CLOSED_PROPERLY,

    /** Current VFS impl version != VFS on-disk format version */
    IMPL_VERSION_MISMATCH
  }
}
