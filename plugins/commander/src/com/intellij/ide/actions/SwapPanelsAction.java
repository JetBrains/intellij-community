
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.commander.Commander;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

public class SwapPanelsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    Commander.getInstance(project).swapPanels();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    String id = windowManager.getActiveToolWindowId();
    presentation.setEnabled(ToolWindowId.COMMANDER.equals(id));
  }
}
