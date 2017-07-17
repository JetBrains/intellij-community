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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HgBranchUtil {

  /**
   * Only common hg heavy branches
   */
  @NotNull
  public static List<String> getCommonBranches(@NotNull Collection<HgRepository> repositories) {
    return getCommonNames(repositories, false);
  }

  @NotNull
  public static List<String> getCommonBookmarks(@NotNull Collection<HgRepository> repositories) {
    return getCommonNames(repositories, true);
  }

  private static List<String> getCommonNames(@NotNull Collection<HgRepository> repositories, boolean bookmarkType) {
    Collection<String> common = null;
    for (HgRepository repository : repositories) {
      Collection<String> names =
        bookmarkType ? HgUtil.getSortedNamesWithoutHashes(repository.getBookmarks()) : repository.getOpenedBranches();
      common = common == null ? names : ContainerUtil.intersection(common, names);
    }
    return common != null ? StreamEx.of(common).sorted(StringUtil::naturalCompare).toList() : Collections.emptyList();
  }
}
