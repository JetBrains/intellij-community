/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElement;
import com.intellij.openapi.diff.impl.dir.DirDiffOperation;
import com.intellij.openapi.diff.impl.dir.DirDiffPanel;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SetOperationToBase extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DirDiffOperation operation = getOperation();
    final DirDiffTableModel model = getModel(e);
    final JTable table = getTable(e);
    assert model != null && table != null;
    for (DirDiffElement element : model.getSelectedElements()) {
      element.setOperation(operation);
    }
    table.repaint();
  }

  @NotNull
  protected abstract DirDiffOperation getOperation();

  @Override
  public void update(AnActionEvent e) {
    final DirDiffTableModel model = getModel(e);
    final JTable table = getTable(e);
    e.getPresentation().setEnabled(table != null
                                   && model != null
                                   && !model.getSelectedElements().isEmpty());
  }

  @Nullable
  private static JTable getTable(AnActionEvent e) {
    return e.getData(DirDiffPanel.DIR_DIFF_TABLE);
  }

  @Nullable
  public static DirDiffTableModel getModel(AnActionEvent e) {
    return e.getData(DirDiffPanel.DIR_DIFF_MODEL);
  }
}
