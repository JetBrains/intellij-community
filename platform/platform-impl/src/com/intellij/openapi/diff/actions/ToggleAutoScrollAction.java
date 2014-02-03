/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ToggleActionButton;

import javax.swing.*;

public class ToggleAutoScrollAction extends ToggleActionButton implements DumbAware {
  public ToggleAutoScrollAction() {
    super("Auto Scroll", AllIcons.General.AutoscrollToSource);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    DiffPanelEx diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    if (diffPanel != null) {
      return diffPanel.isAutoScrollEnabled();
    }
    else {
      return true;
    }
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    if (diffPanel != null) {
      diffPanel.setAutoScrollEnabled(state);
    }
  }
}
