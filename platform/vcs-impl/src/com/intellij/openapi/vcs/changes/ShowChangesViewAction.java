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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 18.08.2006
 * Time: 16:35:20
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vcs.actions.AbstractVcsAction;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindow;

public class ShowChangesViewAction extends AbstractVcsAction {
  protected void actionPerformed(VcsContext e) {
    if (e.getProject() == null) return;
    final ToolWindowManager manager = ToolWindowManager.getInstance(e.getProject());
    if (manager != null) {
      final ToolWindow window = manager.getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);
      if (window != null) {
        window.show(null);
      }
    }
  }

  protected void update(VcsContext vcsContext, Presentation presentation) {
    presentation.setVisible(getActiveVcses(vcsContext).size() > 0);
  }

  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }
}
