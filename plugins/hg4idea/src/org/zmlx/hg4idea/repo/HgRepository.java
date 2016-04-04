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

package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgNameWithHashInfo;

import java.util.*;


public interface HgRepository extends Repository {
  @NotNull String DEFAULT_BRANCH = "default";

  @NotNull
  VirtualFile getHgDir();

  /**
   * Returns the current branch of this Hg repository.
   */

  @NotNull
  String getCurrentBranch();

  /**
   * @return map with heavy branch names and appropriate set of head hashes, order of heads is important - the last head in file is the main
   */
  @NotNull
  Map<String, LinkedHashSet<Hash>> getBranches();

  /**
   * @return names of opened heavy branches
   */
  @NotNull
  Set<String> getOpenedBranches();

  @NotNull
  Collection<HgNameWithHashInfo> getBookmarks();

  @NotNull
  Collection<HgNameWithHashInfo> getTags();

  @NotNull
  Collection<HgNameWithHashInfo> getLocalTags();

  @Nullable
  String getCurrentBookmark();

  @Nullable
  String getTipRevision();

  @NotNull
  HgConfig getRepositoryConfig();

  boolean hasSubrepos();

  @NotNull
  Collection<HgNameWithHashInfo> getSubrepos();

  @NotNull
  List<HgNameWithHashInfo> getMQAppliedPatches();

  @NotNull
  List<String> getAllPatchNames();

  @NotNull
  List<String> getUnappliedPatchNames();

  void updateConfig();
}
