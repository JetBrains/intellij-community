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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diff.impl.dir.DirDiffElement;
import com.intellij.openapi.diff.impl.dir.DirDiffOperation;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class SynchronizeDiff extends DirDiffAction {
  private boolean mySelectedOnly;

  public SynchronizeDiff(DirDiffTableModel model, boolean selectedOnly) {
    super(model,
          selectedOnly ? "Synchronize Selected" : "Synchronize All",
          IconLoader.getIcon(selectedOnly ? "/actions/resume.png" : "/actions/refreshUsages.png"));
    mySelectedOnly = selectedOnly;
  }

  @Override
  protected void updateState(boolean state) {
    final List<DirDiffElement> elements = mySelectedOnly ? getModel().getSelectedElements() : getModel().getElements();    
    
    for (DirDiffElement element : elements) {
      if (mySelectedOnly) {
        getModel().rememberSelection();
      }

      final DirDiffOperation operation = element.getOperation();
      if (operation == null) continue;
      switch (operation) {
        case COPY_TO:
          getModel().performCopyTo(element);
          break;
        case COPY_FROM:
          getModel().performCopyFrom(element);
          break;
        case MERGE:
          break;
        case EQUAL:
          break;
        case NONE:
          break;
        case DELETE:
          getModel().performDelete(element);
          break;
      }
    }    
    
    if (!mySelectedOnly) {
      getModel().selectFirstRow();
    }
  }

  @Override
  public ShortcutSet getShortcut() {
    return CustomShortcutSet.fromString(mySelectedOnly ? "ENTER" : SystemInfo.isMac ? "meta ENTER" : "control ENTER");
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return false;
  }

  @Override
  protected boolean isFullReload() {
    return true;
  }
}
