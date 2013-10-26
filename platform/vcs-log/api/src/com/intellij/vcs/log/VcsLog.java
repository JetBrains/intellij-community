/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Use this interface to access information available in the VCS Log.
 */
public interface VcsLog {

  /**
   * Returns commits currently selected in the log.
   */
  @NotNull
  List<VcsFullCommitDetails> getSelectedCommits();

  /**
   * Returns names of branches which contain the given commit, or null if this information is unavailable.
   */
  @Nullable
  Collection<String> getContainingBranches(@NotNull Hash commitHash);

}
