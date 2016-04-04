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
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Map of {@link Hash} to <b>{@code int}</b>.
 */
public interface VcsLogHashMap {

  int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root);

  @NotNull
  CommitId getCommitId(int commitIndex);

  /**
   * Iterates over known commid id to find the first one which satisfies given condition.
   *
   * @return
   */
  @Nullable
  CommitId findCommitId(@NotNull Condition<CommitId> condition);
}
