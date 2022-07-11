// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
public final class ChangesViewWorkflowManager {
  @NotNull private final Project myProject;

  @NotNull
  public static ChangesViewWorkflowManager getInstance(@NotNull Project project) {
    return project.getService(ChangesViewWorkflowManager.class);
  }

  public ChangesViewWorkflowManager(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  public ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return ((ChangesViewManager)ChangesViewManager.getInstance(myProject)).getCommitWorkflowHandler();
  }
}
