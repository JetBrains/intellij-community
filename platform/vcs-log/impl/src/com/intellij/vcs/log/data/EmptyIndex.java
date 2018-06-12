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
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogDetailsFilter;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.VcsLogIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class EmptyIndex implements VcsLogIndex {
  @Override
  public void scheduleIndex(boolean full) {
  }

  @Override
  public boolean isIndexed(int commit) {
    return false;
  }

  @Override
  public boolean isIndexed(@NotNull VirtualFile root) {
    return false;
  }

  @Override
  public boolean isIndexingEnabled(@NotNull VirtualFile root) {
    return false;
  }

  @Override
  public void markForIndexing(int commit, @NotNull VirtualFile root) {
  }

  @Override
  public void reindexWithRenames(int commit, @NotNull VirtualFile root) {
  }

  @Override
  public boolean canFilter(@NotNull List<VcsLogDetailsFilter> filters) {
    return false;
  }

  @NotNull
  @Override
  public Set<Integer> filter(@NotNull List<VcsLogDetailsFilter> detailsFilters) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public IndexDataGetter getDataGetter() {
    return null;
  }

  @Override
  public void addListener(@NotNull IndexingFinishedListener l) {
  }

  @Override
  public void removeListener(@NotNull IndexingFinishedListener l) {
  }

  @Override
  public void markCorrupted() {
  }
}
