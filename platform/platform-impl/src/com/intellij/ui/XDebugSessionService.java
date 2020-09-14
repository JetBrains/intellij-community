// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class XDebugSessionService {
  public static XDebugSessionService getInstance(@NotNull Project project) {
    return project.getService(XDebugSessionService.class);
  }

  public abstract boolean hasActiveDebugSession(@NotNull Project project);
}
