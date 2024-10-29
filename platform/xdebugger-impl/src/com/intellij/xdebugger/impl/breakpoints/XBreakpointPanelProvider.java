// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class XBreakpointPanelProvider extends BreakpointPanelProvider<XBreakpoint> {

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
  @Nullable
  public XBreakpoint<?> findBreakpoint(@NotNull final Project project, @NotNull final Document document, final int offset) {
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    int line = document.getLineNumber(offset);
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) {
      return null;
    }
    for (XLineBreakpointType<?> type : XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      XLineBreakpoint<? extends XBreakpointProperties> breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
      if (breakpoint != null) {
        return breakpoint;
      }
    }

    return null;
  }

  @Override
  public GutterIconRenderer getBreakpointGutterIconRenderer(Object breakpoint) {
    if (breakpoint instanceof XLineBreakpointImpl) {
      RangeHighlighter highlighter = ((XLineBreakpointImpl<?>)breakpoint).getHighlighter();
      if (highlighter != null) {
        return highlighter.getGutterIconRenderer();
      }
    }
    return null;
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
