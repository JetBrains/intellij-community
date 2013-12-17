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
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * Responsible for identifying commits which are displayed around the selected commit in the log table. <br/>
 * This is needed to be able to smartly get commit details from the VCS in a batch.
 *
 * @param <CommitId> Commit identifier, which can be, for example, a {@link Hash} or an {@link Integer} or a {@link Node}.
 */
public interface AroundProvider<CommitId> {

  /**
   * Returns commits which are located around the given commit in the log table.<br/>
   * The returned map must include the commit which was requested originally.<br/>
   * Commits must be grouped by repository roots.
   *
   * @param id    currently selected commit id.
   * @param above number of commits to look above (later, by date) the given commit.
   * @param below number of commits to look below (before, by date) the given commit.
   * @return hashes of commits located around the given one, grouped by repository roots.
   */
  @NotNull
  MultiMap<VirtualFile, Hash> getCommitsAround(@NotNull CommitId id, int above, int below);

  /**
   * Returns the Hash of the commit identified by the given CommitId.
   */
  @NotNull
  Hash resolveId(@NotNull CommitId id);

}
