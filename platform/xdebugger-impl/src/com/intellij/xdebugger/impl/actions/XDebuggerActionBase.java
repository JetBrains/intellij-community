/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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
      presentation.setEnabledAndVisible(false);
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
    Project project = e.getProject();
    if (project != null && !project.isDisposed()) {
      return Arrays.stream(DebuggerSupport.getDebuggerSupports()).anyMatch(support -> isEnabled(project, e, support));
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
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return true;
    }

    for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
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
    Project project = event.getProject();
    if (project != null && !project.isDisposed()) {
      return Arrays.stream(DebuggerSupport.getDebuggerSupports()).allMatch(support -> getHandler(support).isHidden(project, event));
    }
    return true;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
