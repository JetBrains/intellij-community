/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

public class EditBreakpointAction extends XDebuggerActionBase implements DumbAware {

  public static class ContextAction extends DumbAwareAction {
    private final GutterIconRenderer myRenderer;
    private final Object myBreakpoint;
    private final DebuggerSupport myDebuggerSupport;

    public ContextAction(GutterIconRenderer breakpointRenderer, Object breakpoint, DebuggerSupport debuggerSupport) {
      super(ActionsBundle.actionText("EditBreakpoint"));
      myRenderer = breakpointRenderer;
      myBreakpoint = breakpoint;
      myDebuggerSupport = debuggerSupport;
      AnAction action = ActionManager.getInstance().getAction("ViewBreakpoints");
      copyShortcutFrom(action);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
      Project project = getEventProject(e);
      if (editor == null || project == null) return;
      myDebuggerSupport.getEditBreakpointAction().editBreakpoint(project, editor, myBreakpoint, myRenderer);
    }
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getEditBreakpointAction();
  }
}
