// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;

import java.util.Collection;

@Deprecated
public abstract class BreakpointPanelProvider {
  public abstract void provideBreakpointItems(Project project, Collection<? super BreakpointItem> items);
}
