// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import org.jetbrains.annotations.NotNull;

public abstract class FrameTitleBuilder {
  @RequiresBlockingContext
  public static FrameTitleBuilder getInstance() {
    return ApplicationManager.getApplication().getService(FrameTitleBuilder.class);
  }

  public abstract String getProjectTitle(@NotNull Project project);

  public abstract String getFileTitle(@NotNull Project project, @NotNull VirtualFile file);
}