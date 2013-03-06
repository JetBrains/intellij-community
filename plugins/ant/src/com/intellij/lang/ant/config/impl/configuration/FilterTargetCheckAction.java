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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.AnActionButton;

import javax.swing.*;

/**
 * @author VISTALL
 * @date 14:36/06.03.13
 */
public class FilterTargetCheckAction extends AnActionButton {
  private final boolean isMark;
  private final JTable myTable;

  public FilterTargetCheckAction(boolean mark, JTable table) {
    super(AntBundle.message(mark ? "filter.target.panel.check.selected" : "filter.target.panel.uncheck.selected"), mark ? AllIcons.Actions.Selectall : AllIcons.Actions.Unselectall);
    isMark = mark;
    myTable = table;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final int[] selectedRows = myTable.getSelectedRows();
    for (int i = 0; i < selectedRows.length; i++) {
      int selectedRow = selectedRows[i];

      myTable.getModel().setValueAt(Boolean.valueOf(isMark), selectedRow, 0);
    }
    myTable.repaint();
  }
}
