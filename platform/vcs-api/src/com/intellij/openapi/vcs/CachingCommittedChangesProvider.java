// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

public interface CachingCommittedChangesProvider<T extends CommittedChangeList, U extends ChangeBrowserSettings>
  extends CommittedChangesProvider<T, U> {
  /**
   * Returns the current version of the binary data format that is read/written by the caching provider.
   * If the format version loaded from the cache stream does not match the format version returned by
   * the provider, the cache stream is discarded and changes are reloaded from server.
   *
   * @return binary format version.
   */
  int getFormatVersion();

  void writeChangeList(@NotNull DataOutput stream, @NotNull T list) throws IOException;

  @NotNull
  T readChangeList(@NotNull RepositoryLocation location, @NotNull DataInput stream) throws IOException;

  /**
   * Returns true if the underlying VCS allows to limit the number of loaded changes. If the VCS does not
   * support that, filtering by date will be used when initializing history cache.
   *
   * @return true if number limit is supported, false otherwise.
   */
  default boolean isMaxCountSupported() {
    return true;
  }

  /**
   * Returns the list of files under the specified repository root which may contain incoming changes.
   * This method is an optional optimization: if null is returned, all files are checked through DiffProvider
   * in a regular way.
   *
   * @param location the location where changes are requested.
   * @return the files which may contain the changes, or null if the call is not supported.
   */
  default @Nullable Collection<FilePath> getIncomingFiles(@NotNull RepositoryLocation location) throws VcsException {
    return null;
  }

  /**
   * Returns true if the changelist number restriction should be used when refreshing the cache,
   * or false if the date restriction should be used.
   *
   * @return true if restrict by number, false if restrict by date
   */
  default boolean refreshCacheByNumber() {
    return true;
  }

  /**
   * Returns the name of the "changelist" concept in the specified VCS (changelist, revision etc.)
   *
   * @return the name of the concept, or null if the VCS (like CVS) does not use changelist numbering.
   */
  @Nullable
  @Nls
  String getChangelistTitle();

  default boolean isChangeLocallyAvailable(@NotNull FilePath filePath,
                                           @Nullable VcsRevisionNumber localRevision,
                                           @NotNull VcsRevisionNumber changeRevision,
                                           @NotNull T changeList) {
    return localRevision != null && localRevision.compareTo(changeRevision) >= 0;
  }

  /**
   * Returns true if a timer-based refresh of committed changes should be followed by refresh of incoming changes, so that,
   * for example, changes from the wrong branch would be automatically removed from view.
   *
   * @return true if auto-refresh includes incoming changes refresh, false otherwise
   */
  boolean refreshIncomingWithCommitted();
}
