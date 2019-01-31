/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.zmlx.hg4idea.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.TableUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragSource;

public class TableRowsTransferHandler extends TransferHandler {
  private static final Logger LOG = Logger.getInstance(TableRowsTransferHandler.class);
  private static final DataFlavor ourFlavor = new DataFlavor(RowsDragInfo.class, DataFlavor.stringFlavor.getMimeType());
  private final JTable myTable;

  public TableRowsTransferHandler(JTable table) {
    myTable = table;
  }

  @Override
  protected Transferable createTransferable(JComponent c) {
    assert (c == myTable);
    return new Transferable() {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ourFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return ArrayUtil.contains(flavor, getTransferDataFlavors());
      }

      @NotNull
      @Override
      public Object getTransferData(DataFlavor flavor) {
        return new RowsDragInfo(myTable.getSelectedRows());
      }
    };
  }

  @Override
  public boolean canImport(TransferHandler.TransferSupport support) {
    boolean canImport = support.getComponent() == myTable && support.isDrop();
    myTable.setCursor(canImport ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop);
    return canImport;
  }

  @Override
  public int getSourceActions(JComponent c) {
    return TransferHandler.COPY_OR_MOVE;
  }

  @Override
  public boolean importData(TransferHandler.TransferSupport support) {
    TableModel tableModel = myTable.getModel();
    if (!(tableModel instanceof MultiReorderedModel) || !((MultiReorderedModel)tableModel).canMoveRows()) return false;

    JTable.DropLocation dl = (JTable.DropLocation)support.getDropLocation();
    int index = dl.getRow();
    int max = tableModel.getRowCount();
    if (index < 0 || index > max) {
      index = max;
    }
    myTable.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

    try {
      int[] rows = ((RowsDragInfo)support.getTransferable().getTransferData(ourFlavor)).myRows;
      if (rows != null && rows.length > 0) {
        int dist = 0;
        for (int row : rows) {
          if (index > row) {
            dist++;
          }
        }
        index -= dist;
        int[] newSelection = ((MultiReorderedModel)tableModel).moveRows(rows, index);
        TableUtil.selectRows(myTable, newSelection);
        return true;
      }
    }
    catch (Exception e) {
      LOG.error("Couldn't move transferred data.");
    }
    return false;
  }

  private static class RowsDragInfo {
    final int[] myRows;

    RowsDragInfo(int[] rows) {
      myRows = rows;
    }
  }
}