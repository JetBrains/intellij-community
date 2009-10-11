/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

/**
 * @author yole
 * @since 7.0
 */
public interface CachingCommittedChangesProvider<T extends CommittedChangeList, U extends ChangeBrowserSettings> extends CommittedChangesProvider<T, U> {
  /**
   * Returns the current version of the binary data format that is read/written by the caching provider.
   * If the format version loaded from the cache stream does not match the format version returned by
   * the provider, the cache stream is discarded and changes are reloaded from server.
   *
   * @return binary format version.
   */
  int getFormatVersion();
  void writeChangeList(final DataOutput stream, final T list) throws IOException;
  T readChangeList(final RepositoryLocation location, final DataInput stream) throws IOException;

  /**
   * Returns true if the underlying VCS allows to limit the number of loaded changes. If the VCS does not
   * support that, filtering by date will be used when initializing history cache.
   *
   * @return true if number limit is supported, false otherwise.
   */
  boolean isMaxCountSupported();

  /**
   * Returns the list of files under the specified repository root which may contain incoming changes.
   * This method is an optional optimization: if null is returned, all files are checked through DiffProvider
   * in a regular way.
   *
   * @param location the location where changes are requested.
   * @return the files which may contain the changes, or null if the call is not supported.
   */
  @Nullable
  Collection<FilePath> getIncomingFiles(final RepositoryLocation location) throws VcsException;

  /**
   * Returns true if the changelist number restriction should be used when refreshing the cache,
   * or false if the date restriction should be used.
   *
   * @return true if restrict by number, false if restrict by date
   */
  boolean refreshCacheByNumber();

  /**
   * Returns the name of the "changelist" concept in the specified VCS (changelist, revision etc.)
   *
   * @return the name of the concept, or null if the VCS (like CVS) does not use changelist numbering.
   */
  @Nullable @Nls
  String getChangelistTitle();

  boolean isChangeLocallyAvailable(FilePath filePath, @Nullable VcsRevisionNumber localRevision, VcsRevisionNumber changeRevision, T changeList);

  /**
   * Returns true if a timer-based refresh of committed changes should be followed by refresh of incoming changes, so that,
   * for example, changes from the wrong branch would be automatically removed from view.
   *
   * @return true if auto-refresh includes incoming changes refresh, false otherwise
   */
  boolean refreshIncomingWithCommitted();
}
