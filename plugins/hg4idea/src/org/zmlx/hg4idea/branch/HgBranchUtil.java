/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.branch;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HgBranchUtil {

  /**
   * Only common hg heavy branches
   */
  @NotNull
  public static List<String> getCommonBranches(@NotNull Collection<HgRepository> repositories) {
    Collection<String> commonBranches = null;
    for (HgRepository repository : repositories) {
      Collection<String> names = repository.getOpenedBranches();
      if (commonBranches == null) {
        commonBranches = names;
      }
      else {
        commonBranches = ContainerUtil.intersection(commonBranches, names);
      }
    }
    if (commonBranches != null) {
      ArrayList<String> common = new ArrayList<>(commonBranches);
      Collections.sort(common);
      return common;
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  public static List<String> getCommonBookmarks(@NotNull Collection<HgRepository> repositories) {
    Collection<String> commonBookmarkNames = null;
    for (HgRepository repository : repositories) {
      Collection<HgNameWithHashInfo> bookmarksInfo = repository.getBookmarks();
      Collection<String> names = HgUtil.getSortedNamesWithoutHashes(bookmarksInfo);
      if (commonBookmarkNames == null) {
        commonBookmarkNames = names;
      }
      else {
        commonBookmarkNames = ContainerUtil.intersection(commonBookmarkNames, names);
      }
    }
    if (commonBookmarkNames != null) {
      ArrayList<String> common = new ArrayList<>(commonBookmarkNames);
      Collections.sort(common);
      return common;
    }
    else {
      return Collections.emptyList();
    }
  }
}
