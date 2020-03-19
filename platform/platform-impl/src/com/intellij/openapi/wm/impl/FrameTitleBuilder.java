// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class FrameTitleBuilder {
  public static FrameTitleBuilder getInstance() {
    return ApplicationManager.getApplication().getService(FrameTitleBuilder.class);
  }

  public abstract String getProjectTitle(@NotNull Project project);

  public abstract String getFileTitle(@NotNull Project project, @NotNull VirtualFile file);
}