// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class   XLineBreakpointTypeBase extends XLineBreakpointType<XBreakpointProperties> {
  private final XDebuggerEditorsProvider myEditorsProvider;

  protected XLineBreakpointTypeBase(final @NonNls @NotNull String id, final @Nls @NotNull String title, @Nullable XDebuggerEditorsProvider editorsProvider) {
    super(id, title);

    myEditorsProvider = editorsProvider;
  }

  @Override
  public @Nullable XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint, @NotNull Project project) {
    return myEditorsProvider;
  }

  @Override
  public @Nullable XBreakpointProperties createBreakpointProperties(final @NotNull VirtualFile file, final int line) {
    return null;
  }
}