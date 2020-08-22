// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunnableBackgroundableWrapper extends Task.Backgroundable {
  private final Runnable myNonCancellable;

  public RunnableBackgroundableWrapper(@Nullable Project project, @NotNull @ProgressTitle String title, Runnable nonCancellable) {
    super(project, title, false);
    myNonCancellable = nonCancellable;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    myNonCancellable.run();
  }
}
