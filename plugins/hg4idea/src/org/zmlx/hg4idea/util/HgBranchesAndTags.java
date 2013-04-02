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
package org.zmlx.hg4idea.util;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgTagBranch;

import java.util.List;
import java.util.Map;

/**
 * @author Nadya Zabrodina
 */

public class HgBranchesAndTags {

  @NotNull private final Map<VirtualFile, List<HgTagBranch>> branchesForRepos = new HashMap<VirtualFile, List<HgTagBranch>>();
  @NotNull private final Map<VirtualFile, List<HgTagBranch>> tagsForRepos = new HashMap<VirtualFile, List<HgTagBranch>>();


  @NotNull
  public Map<VirtualFile, List<HgTagBranch>> getBranchesForRepos() {
    return branchesForRepos;
  }

  public void addBranches(@NotNull VirtualFile repo, @NotNull List<HgTagBranch> branches) {
    branchesForRepos.put(repo, branches);
  }

  @NotNull
  public Map<VirtualFile, List<HgTagBranch>> getTagsForRepos() {
    return tagsForRepos;
  }

  public void addTags(@NotNull VirtualFile repo, @NotNull List<HgTagBranch> tags) {
    tagsForRepos.put(repo, tags);
  }
}