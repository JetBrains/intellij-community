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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.DiffViewer;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ToggleActionButton;

public class ToggleAutoScrollAction extends ToggleActionButton implements DumbAware {
  public ToggleAutoScrollAction() {
    super("Synchronize Scrolling", AllIcons.Actions.SynchronizeScrolling);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    DiffViewer viewer = PlatformDataKeys.DIFF_VIEWER.getData(e.getDataContext());
    if (viewer instanceof DiffPanelEx) return ((DiffPanelEx)viewer).isAutoScrollEnabled();
    if (viewer instanceof MergePanel2) return ((MergePanel2)viewer).isAutoScrollEnabled();
    return true;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    DiffViewer viewer = PlatformDataKeys.DIFF_VIEWER.getData(e.getDataContext());
    if (viewer instanceof DiffPanelEx) ((DiffPanelEx)viewer).setAutoScrollEnabled(state);
    if (viewer instanceof MergePanel2) ((MergePanel2)viewer).setAutoScrollEnabled(state);
  }
}
