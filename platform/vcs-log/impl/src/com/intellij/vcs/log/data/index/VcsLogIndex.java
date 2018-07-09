/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface VcsLogIndex {
  void scheduleIndex(boolean full);

  boolean isIndexed(int commit);

  boolean isIndexed(@NotNull VirtualFile root);

  boolean isIndexingEnabled(@NotNull VirtualFile root);

  void markForIndexing(int commit, @NotNull VirtualFile root);

  void reindexWithRenames(int commit, @NotNull VirtualFile root);

  boolean canFilter(@NotNull List<VcsLogDetailsFilter> filters);

  @NotNull
  Set<Integer> filter(@NotNull List<VcsLogDetailsFilter> detailsFilters);

  @Nullable
  IndexDataGetter getDataGetter();

  void markCorrupted();

  void addListener(@NotNull IndexingFinishedListener l);

  void removeListener(@NotNull IndexingFinishedListener l);

  interface IndexingFinishedListener {
    void indexingFinished(@NotNull VirtualFile root);
  }
}
