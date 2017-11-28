/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class MuteBreakpointAction extends ToggleAction {
  @Override
  public boolean isSelected(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerToggleActionHandler handler = support.getMuteBreakpointsHandler();
        if (handler.isEnabled(project, e)) {
          return handler.isSelected(project, e);
        }
      }
    }
    return false;
  }

  @Override
  public void setSelected(final AnActionEvent e, final boolean state) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerToggleActionHandler handler = support.getMuteBreakpointsHandler();
        if (handler.isEnabled(project, e)) {
          handler.setSelected(project, e, state);
          return;
        }
      }
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        DebuggerToggleActionHandler handler = support.getMuteBreakpointsHandler();
        if (handler.isEnabled(project, e)) {
          e.getPresentation().setEnabled(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabled(false);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
