/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.vcs.log.VcsShortCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DataGetter<T extends VcsShortCommitDetails> {
  @NotNull
  T getCommitData(int hash, @NotNull Iterable<Integer> neighbourHashes);

  @Deprecated
  default void loadCommitsData(@NotNull List<Integer> hashes,
                               @NotNull Consumer<List<T>> consumer,
                               @Nullable ProgressIndicator indicator) {
    loadCommitsData(hashes, consumer, EmptyConsumer.getInstance(), indicator);
  }

  void loadCommitsData(@NotNull List<Integer> hashes, @NotNull Consumer<List<T>> consumer,
                       @NotNull Consumer<Throwable> errorConsumer, @Nullable ProgressIndicator indicator);

  @Nullable
  T getCommitDataIfAvailable(int hash);
}
