/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.vcs.log.ui.table.VcsLogNewUiTableCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;

public class SimpleColoredComponentLinkMouseListener extends TableLinkMouseListener {
  @Override
  protected Object tryGetTag(@NotNull MouseEvent e, @NotNull JTable table, int row, int column, @NotNull TableCellRenderer cellRenderer) {
    Component component = cellRenderer.getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
    if (component instanceof SimpleColoredComponent) {
      Rectangle rc = table.getCellRect(row, column, false);

      // see also com.intellij.vcs.log.ui.table.VcsLogGraphTable.getPointInCell
      int additionalOffset = 0;
      if (ExperimentalUI.isNewUI() && column == VcsLogNewUiTableCellRenderer.ROOT_COLUMN_INDEX + 1) {
        additionalOffset = JBUIScale.scale(VcsLogNewUiTableCellRenderer.getAdditionalGap());
      }

      return ((SimpleColoredComponent)component).getFragmentTagAt(e.getX() - rc.x - additionalOffset);
    }
    return super.tryGetTag(e, table, row, column, cellRenderer);
  }
}
