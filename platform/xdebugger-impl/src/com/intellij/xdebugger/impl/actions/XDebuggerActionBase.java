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
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerActionBase extends AnAction implements AnAction.TransparentUpdate {
  private final boolean myHideDisabledInPopup;

  protected XDebuggerActionBase() {
    this(false);
  }

  protected XDebuggerActionBase(final boolean hideDisabledInPopup) {
    myHideDisabledInPopup = hideDisabledInPopup;
  }

  @Override
  public void update(final AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    boolean hidden = isHidden(event);
    if (hidden) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    boolean enabled = isEnabled(event);
    if (myHideDisabledInPopup && ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setVisible(true);
    }
    presentation.setEnabled(enabled);
  }

  protected boolean isEnabled(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
      for (DebuggerSupport support : debuggerSupports) {
        if (isEnabled(project, e, support)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  protected abstract DebuggerActionHandler getHandler(@NotNull DebuggerSupport debuggerSupport);

  private boolean isEnabled(final Project project, final AnActionEvent event, final DebuggerSupport support) {
    return getHandler(support).isEnabled(project, event);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    performWithHandler(e);
  }

  protected boolean performWithHandler(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return true;
    }

    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport support : debuggerSupports) {
      if (isEnabled(project, e, support)) {
        perform(project, e, support);
        return true;
      }
    }
    return false;
  }

  private void perform(final Project project, final AnActionEvent e, final DebuggerSupport support) {
    getHandler(support).perform(project, e);
  }

  protected boolean isHidden(AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        if (!getHandler(support).isHidden(project, event)) {
          return false;
        }
      }
    }
    return true;
  }
}
