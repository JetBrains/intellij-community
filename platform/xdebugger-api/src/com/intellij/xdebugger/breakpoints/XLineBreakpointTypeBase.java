package com.intellij.xdebugger.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XLineBreakpointTypeBase extends XLineBreakpointType<XBreakpointProperties> {
  private final XDebuggerEditorsProvider myEditorsProvider;

  protected XLineBreakpointTypeBase(@NonNls @NotNull final String id, @Nls @NotNull final String title, @Nullable XDebuggerEditorsProvider editorsProvider) {
    super(id, title);

    myEditorsProvider = editorsProvider;
  }

  @Nullable
  @Override
  public XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint, @NotNull Project project) {
    return myEditorsProvider;
  }

  @Override
  @Nullable
  public XBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
    return null;
  }
}