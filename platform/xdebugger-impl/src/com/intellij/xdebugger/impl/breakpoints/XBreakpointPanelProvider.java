// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class XBreakpointPanelProvider extends BreakpointPanelProvider {

  @Override
  public void addListener(final BreakpointsListener listener, Project project, Disposable disposable) {
    project.getMessageBus().connect(disposable).subscribe(XBreakpointListener.TOPIC, new MyXBreakpointListener(listener,
                                                                                                               XDebuggerManager.getInstance(project).getBreakpointManager()));
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public void onDialogClosed(final Project project) {
  }

  @Override
  public void provideBreakpointItems(Project project, Collection<? super BreakpointItem> items) {
    Arrays.stream(XDebuggerManager.getInstance(project).getBreakpointManager().getAllBreakpoints())
      .map(XBreakpointItem::new)
      .forEach(items::add);
  }

  private static class MyXBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    public final BreakpointsListener myListener;
    public final XBreakpointManager myBreakpointManager;

    MyXBreakpointListener(BreakpointsListener listener, XBreakpointManager breakpointManager) {
      myListener = listener;
      myBreakpointManager = breakpointManager;
    }

    @Override
    public void breakpointAdded(@NotNull XBreakpoint<?> breakpoint) {
      myListener.breakpointsChanged();
    }

    @Override
    public void breakpointRemoved(@NotNull XBreakpoint<?> breakpoint) {
      myListener.breakpointsChanged();
    }

    @Override
    public void breakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
      myListener.breakpointsChanged();
    }
  }
}
