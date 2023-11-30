// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/** Carries errorCategory so different categories frequencies could be monitored */
@ApiStatus.Internal
public final class VFSInitException extends IOException {
  private final @NotNull ErrorCategory errorCategory;

  public VFSInitException(@NotNull ErrorCategory errorCategory,
                          @NotNull String message) {
    super(message);
    this.errorCategory = errorCategory;
  }

  public VFSInitException(@NotNull ErrorCategory errorCategory,
                          @NotNull String message,
                          @NotNull Throwable cause) {
    super(message, cause);
    this.errorCategory = errorCategory;
  }

  public ErrorCategory category() {
    return errorCategory;
  }

  @Override
  public String toString() {
    return "[" + errorCategory + "]: " + getMessage();
  }

  public enum ErrorCategory {
    /** Rebuild marker was found */
    SCHEDULED_REBUILD,

    /** Caches were recovered from VfsLog, because of residual initialization problems */
    RECOVERED_FROM_LOG,

    /** VFS wasn't closed properly -> VFS storages could be in inconsistent state */
    NOT_CLOSED_PROPERLY,
    /** There were VFS errors in previous session (that may indicate corruptions) */
    HAS_ERRORS_IN_PREVIOUS_SESSION,

    /** Current VFS implementation (i.e. code) version != VFS on-disk format version */
    IMPL_VERSION_MISMATCH,

    /** Name storage is not able to resolve existing reference */
    NAME_STORAGE_INCOMPLETE,
    /** Attributes storage has corrupted record(s) */
    ATTRIBUTES_STORAGE_CORRUPTED,
    /** Content and ContentHashes storages are not match with each other */
    CONTENT_STORAGES_NOT_MATCH,
    /** Content or ContentHashes storages are not able to resolve existing reference */
    CONTENT_STORAGES_INCOMPLETE,

    /** Everything else is not covered by the specific constants above */
    UNRECOGNIZED
  }
}
