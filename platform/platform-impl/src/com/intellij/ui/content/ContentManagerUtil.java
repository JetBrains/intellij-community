/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui.content;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class ContentManagerUtil {
  private ContentManagerUtil() {
  }

  /**
   * This is utility method. It returns <code>ContentManager</code> from the current context.
   */
  public static ContentManager getContentManagerFromContext(DataContext dataContext, boolean requiresVisibleToolWindow){
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }

    ToolWindowManagerEx mgr=ToolWindowManagerEx.getInstanceEx(project);

    String id = mgr.getActiveToolWindowId();
    if (id == null) {
      if(mgr.isEditorComponentActive()){
        id = mgr.getLastActiveToolWindowId();
      }
    }
    if(id == null){
      return null;
    }

    ToolWindowEx toolWindow = (ToolWindowEx)mgr.getToolWindow(id);
    if (requiresVisibleToolWindow && !toolWindow.isVisible()) {
      return null;
    }

    final ContentManager fromContext = PlatformDataKeys.CONTENT_MANAGER.getData(dataContext);
    if (fromContext != null) return fromContext;

    return toolWindow != null ? toolWindow.getContentManager() : null;
  }
}
