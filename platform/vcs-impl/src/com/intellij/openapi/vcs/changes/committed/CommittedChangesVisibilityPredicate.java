// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsType;
import com.intellij.util.NotNullFunction;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CommittedChangesVisibilityPredicate implements NotNullFunction<Project, Boolean> {
  @Override
  @NotNull
  public Boolean fun(final Project project) {
    return StreamEx.of(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss()).anyMatch(vcs -> isCommittedChangesAvailable(vcs));
  }

  public static boolean isCommittedChangesAvailable(@NotNull AbstractVcs vcs) {
    return vcs.getCommittedChangesProvider() != null && VcsType.centralized.equals(vcs.getType());
  }
}
