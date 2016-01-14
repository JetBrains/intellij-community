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
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DataGetter<T extends VcsShortCommitDetails> {
  @Nullable
  T getCommitData(int row, @NotNull GraphTableModel tableModel);

  void loadCommitsData(@NotNull List<Integer> rows,
                       @NotNull GraphTableModel tableModel,
                       @NotNull Consumer<List<T>> consumer,
                       @Nullable ProgressIndicator indicator);

  @Nullable
  T getCommitDataIfAvailable(int hash);
}
