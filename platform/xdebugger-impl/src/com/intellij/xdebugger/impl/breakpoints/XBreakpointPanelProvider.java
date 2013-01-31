/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointFileGroupingRule;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointGroupingByTypeRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointPanelProvider extends BreakpointPanelProvider<XBreakpoint> {

  private final List<MyXBreakpointListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public void createBreakpointsGroupingRules(Collection<XBreakpointGroupingRule> rules) {
    rules.add(new XBreakpointGroupingByTypeRule());
    rules.add(new XBreakpointFileGroupingRule());
  }

  @Override
  public void addListener(BreakpointsListener listener, Project project) {
    final MyXBreakpointListener listener1 = new MyXBreakpointListener(listener);
    XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpointListener(listener1);
    myListeners.add(listener1);
  }

  @Override
  public void removeListener(BreakpointsListener listener) {
    for (MyXBreakpointListener breakpointListener : myListeners) {
      if (breakpointListener.myListener == listener) {
        myListeners.remove(breakpointListener);
        break;
      }
    }
  }

  public int getPriority() {
    return 0;
  }

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
      RangeHighlighter highlighter = ((XLineBreakpointImpl)breakpoint).getHighlighter();
      if (highlighter != null) {
        return highlighter.getGutterIconRenderer();
      }
    }
    return null;
  }

  @Override
  public AnAction[] getAddBreakpointActions(@NotNull Project project) {
    List<AnAction> result = new ArrayList<AnAction>();
    for (XBreakpointType<?, ?> type : XBreakpointUtil.getBreakpointTypes()) {
      if (type.isAddBreakpointButtonVisible()) {
        result.add(new AddXBreakpointAction(type));
      }
    }
    return result.toArray(new AnAction[result.size()]);
  }

  public void onDialogClosed(final Project project) {
  }

  @Override
  public void provideBreakpointItems(Project project, Collection<BreakpointItem> items) {
    final XBreakpointType<?, ?>[] types = XBreakpointUtil.getBreakpointTypes();
    final XBreakpointManager manager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XBreakpointType<?, ?> type : types) {
      final Collection<? extends XBreakpoint<?>> breakpoints = manager.getBreakpoints(type);
      if (breakpoints.isEmpty()) continue;
      for (XBreakpoint<?> breakpoint : breakpoints) {
        items.add(new XBreakpointItem(breakpoint));
      }
    }
  }

  private static class MyXBreakpointListener implements XBreakpointListener<XBreakpoint<?>> {
    public BreakpointsListener myListener;

    public MyXBreakpointListener(BreakpointsListener listener) {
      myListener = listener;
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
    }
  }

  private static class AddXBreakpointAction extends AnAction {

    private final XBreakpointType<?, ?> myType;

    public AddXBreakpointAction(XBreakpointType<?, ?> type) {
      myType = type;
      getTemplatePresentation().setIcon(type.getEnabledIcon());
      getTemplatePresentation().setText(type.getTitle());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myType.addBreakpoint(getEventProject(e), null);
    }
  }
}
