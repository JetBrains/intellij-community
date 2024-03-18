// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class HgBranchUtil {

  /**
   * Only common hg heavy branches
   */
  public static @NotNull List<String> getCommonBranches(@NotNull Collection<? extends HgRepository> repositories) {
    return getCommonNames(repositories, false);
  }

  public static @NotNull List<String> getCommonBookmarks(@NotNull Collection<? extends HgRepository> repositories) {
    return getCommonNames(repositories, true);
  }

  private static List<String> getCommonNames(@NotNull Collection<? extends HgRepository> repositories, boolean bookmarkType) {
    Collection<String> common = null;
    for (HgRepository repository : repositories) {
      Collection<String> names =
        bookmarkType ? HgUtil.getSortedNamesWithoutHashes(repository.getBookmarks()) : repository.getOpenedBranches();
      common = common == null ? names : ContainerUtil.intersection(common, names);
    }
    return common != null ? StreamEx.of(common).sorted(StringUtil::naturalCompare).toList() : Collections.emptyList();
  }
}
