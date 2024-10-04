// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public abstract B findBreakpoint(@NotNull Project project, @NotNull Document document, int offset);

  @Nullable
  public abstract GutterIconRenderer getBreakpointGutterIconRenderer(Object breakpoint);

  public abstract void onDialogClosed(final Project project);

  public abstract void provideBreakpointItems(Project project, Collection<? super BreakpointItem> items);
}
