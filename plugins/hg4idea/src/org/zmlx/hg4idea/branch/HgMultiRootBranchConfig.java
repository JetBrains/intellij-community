// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.branch.DvcsMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;

public class HgMultiRootBranchConfig extends DvcsMultiRootBranchConfig<HgRepository> {

  public HgMultiRootBranchConfig(@NotNull Collection<HgRepository> repositories) {
    super(repositories);
  }

  @Override
  public @NotNull Collection<String> getLocalBranchNames() {
    return HgBranchUtil.getCommonBranches(myRepositories);
  }

  @NotNull
  Collection<String> getBookmarkNames() {
    return HgBranchUtil.getCommonBookmarks(myRepositories);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (HgRepository repository : myRepositories) {
      sb.append(repository.getPresentableUrl()).append(":").append(repository.getCurrentBranchName()).append(":")
        .append(repository.getState());
    }
    return sb.toString();
  }
}
