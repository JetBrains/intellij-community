// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.log.HgRefManager;

import java.util.ArrayList;
import java.util.Collection;

public final class HgBranchManager extends DvcsBranchManager {
  public HgBranchManager(@NotNull Project project) {
    super(project, HgProjectSettings.getInstance(project).getBranchSettings(), HgBranchType.values());
  }

  @Override
  protected Collection<String> getDefaultBranchNames(@NotNull BranchType type) {
    ArrayList<String> branches = new ArrayList<>();
    if (type == HgBranchType.BRANCH) {
      branches.add(HgRefManager.DEFAULT);
    }
    return branches;
  }
}
