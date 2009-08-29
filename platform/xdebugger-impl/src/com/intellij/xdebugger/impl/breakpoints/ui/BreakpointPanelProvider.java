package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class BreakpointPanelProvider<B> {
  public abstract int getPriority();

  @Nullable
  public abstract B findBreakpoint(@NotNull Project project, @NotNull Document document, int offset);

  @NotNull
  public abstract Collection<AbstractBreakpointPanel<B>> getBreakpointPanels(@NotNull Project project, @NotNull DialogWrapper parentDialog);

  public abstract void onDialogClosed(final Project project);

}
