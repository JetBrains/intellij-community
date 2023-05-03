// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleColoredComponent;
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

      int additionalOffset = ExperimentalUI.isNewUI() ? VcsLogNewUiTableCellRenderer.getAdditionalOffset(column) : 0;
      return ((SimpleColoredComponent)component).getFragmentTagAt(e.getX() - rc.x - additionalOffset);
    }
    return super.tryGetTag(e, table, row, column, cellRenderer);
  }
}
