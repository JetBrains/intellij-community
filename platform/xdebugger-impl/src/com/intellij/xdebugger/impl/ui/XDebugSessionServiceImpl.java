// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.XDebugSessionService;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;

public class XDebugSessionServiceImpl extends XDebugSessionService {
  @Override
  public boolean hasActiveDebugSession(@NotNull Project project) {
    return XDebuggerManager.getInstance(project).getCurrentSession() != null;
  }
}
