// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/** Carries rebuildCause so different causes frequencies be monitored */
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
    /** No rebuild, VFS initialized and loaded just fine */
    NONE,

    /** No VFS yet -> empty one was built from scratch */
    INITIAL,

    /** Rebuild marker was found */
    SCHEDULED_REBUILD,

    /** Application wasn't closed properly, VFS storages are fractured */
    NOT_CLOSED_PROPERLY,

    /** Current VFS impl (code) version != VFS on-disk format version */
    IMPL_VERSION_MISMATCH,

    /** Name storage is not able to resolve existing reference */
    NAME_STORAGE_INCOMPLETE,
    /** Content and ContentHashes storages are not match with each other */
    CONTENT_STORAGES_NOT_MATCH,
    /** Content or ContentHashes storages are not able to resolve existing reference */
    CONTENT_STORAGES_INCOMPLETE,

    /** Everything else is not covered by the specific constants above */
    UNRECOGNIZED
  }
}
