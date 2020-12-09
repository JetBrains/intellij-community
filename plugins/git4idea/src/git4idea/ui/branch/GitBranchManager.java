// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch;

import com.intellij.dvcs.branch.BranchType;
import com.intellij.dvcs.branch.DvcsBranchManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import git4idea.branch.GitBranchType;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static git4idea.log.GitRefManager.MASTER;
import static git4idea.log.GitRefManager.ORIGIN_MASTER;

@Service(Service.Level.PROJECT)
public final class GitBranchManager extends DvcsBranchManager {
  public GitBranchManager(@NotNull Project project) {
    super(project, GitVcsSettings.getInstance(project).getBranchSettings(), GitBranchType.values());
  }

  @Nullable
  @Override
  protected String getDefaultBranchName(@NotNull BranchType type) {
    if (type == GitBranchType.LOCAL) return MASTER;
    if (type == GitBranchType.REMOTE) return ORIGIN_MASTER;
    return null;
  }
}
