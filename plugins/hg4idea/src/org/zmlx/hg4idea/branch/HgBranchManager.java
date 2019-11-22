// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.log.HgRefManager;

public final class HgBranchManager extends DvcsBranchManager {
  public HgBranchManager(@NotNull Project project) {
    super(HgProjectSettings.getInstance(project).getFavoriteBranchSettings(), HgBranchType.values());
  }

  @Nullable
  @Override
  protected String getDefaultBranchName(@NotNull BranchType type) {
    return type == HgBranchType.BRANCH ? HgRefManager.DEFAULT : null;
  }
}
