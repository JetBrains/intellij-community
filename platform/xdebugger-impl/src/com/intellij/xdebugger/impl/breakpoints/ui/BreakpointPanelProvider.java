// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@Deprecated
public abstract class BreakpointPanelProvider<B> {

  public interface BreakpointsListener {
    void breakpointsChanged();
  }

  public abstract void addListener(BreakpointsListener listener, Project project, Disposable disposable);

  public abstract int getPriority();

  public abstract @Nullable B findBreakpoint(@NotNull Project project, @NotNull Document document, int offset);

  public abstract @Nullable GutterIconRenderer getBreakpointGutterIconRenderer(Object breakpoint);

  public abstract void onDialogClosed(final Project project);

  public abstract void provideBreakpointItems(Project project, Collection<? super BreakpointItem> items);
}
