// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.repo;

import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;
import java.util.Objects;

public final class HgRepositoryManager extends AbstractRepositoryManager<HgRepository> {
  private final HgProjectSettings mySettings;

  public HgRepositoryManager(@NotNull Project project) {
    super(HgVcs.getInstance(project), HgUtil.DOT_HG);
    mySettings = Objects.requireNonNull(HgVcs.getInstance(project)).getProjectSettings();
  }

  @Override
  public boolean isSyncEnabled() {
    return mySettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC && !MultiRootBranches.diverged(getRepositories());
  }

  @NotNull
  @Override
  public List<HgRepository> getRepositories() {
    return getRepositories(HgRepository.class);
  }
}
