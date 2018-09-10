// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class IncomingChangesVisibilityPredicate implements NotNullFunction<Project, Boolean> {
  @Override
  @NotNull
  public Boolean fun(final Project project) {
    final AbstractVcs[] abstractVcses = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    for(AbstractVcs vcs: abstractVcses) {
      CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
      if (provider instanceof CachingCommittedChangesProvider && provider.supportsIncomingChanges()) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }
}
