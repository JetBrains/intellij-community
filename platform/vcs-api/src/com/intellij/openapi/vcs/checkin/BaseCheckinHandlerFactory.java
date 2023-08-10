// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BaseCheckinHandlerFactory {
  @NotNull
  CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext);

  @Nullable
  BeforeCheckinDialogHandler createSystemReadyHandler(@NotNull Project project);
}
