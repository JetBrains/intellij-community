/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class MarkObjectAction extends XDebuggerActionBase {
  @Override
  public void update(AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    boolean enabled = false;
    Presentation presentation = event.getPresentation();
    boolean hidden = true;
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        MarkObjectActionHandler handler = support.getMarkObjectHandler();
        hidden &= handler.isHidden(project, event);
        if (handler.isEnabled(project, event)) {
          enabled = true;
          String text;
          if (handler.isMarked(project, event)) {
            text = ActionsBundle.message("action.Debugger.MarkObject.unmark.text");
          }
          else {
            text = ActionsBundle.message("action.Debugger.MarkObject.text");
          }
          presentation.setText(text);
          break;
        }
      }
    }
    presentation.setVisible(!hidden && (!ActionPlaces.isPopupPlace(event.getPlace()) || enabled));
    presentation.setEnabled(enabled);
  }

  @NotNull
  @Override
  protected DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport) {
    return debuggerSupport.getMarkObjectHandler();
  }
}
