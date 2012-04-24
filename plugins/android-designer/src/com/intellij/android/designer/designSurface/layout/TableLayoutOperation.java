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
package com.intellij.android.designer.designSurface.layout;

import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.android.designer.model.agrid.GridInfo;
import com.intellij.android.designer.model.agrid.GridInsertType;
import com.intellij.android.designer.model.table.RadTableLayoutComponent;
import com.intellij.android.designer.model.table.RadTableRowLayout;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TableLayoutOperation extends GridOperation {
  public TableLayoutOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  @Override
  public void execute() throws Exception {
    GridInfo gridInfo = getGridInfo();

    RadViewComponent container = (RadViewComponent)myContainer;
    List<RadComponent> rows = myContainer.getChildren();
    RadComponent editComponent = myComponents.get(0);

    MetaManager metaManager = ViewsMetaManager.getInstance(container.getTag().getProject());
    MetaModel tableRowModel = metaManager.getModelByTag("TableRow");

    if (myInsertType == GridInsertType.in_cell) {
      if (gridInfo.components != null && myRow < gridInfo.components.length) {
        RadViewComponent rowComponent = (RadViewComponent)rows.get(myRow);
        if (RadTableRowLayout.is(rowComponent)) {
          insertInRow(rowComponent, null, true, myColumn + 1, myColumn);
        }
        else {
          convertToTableRowAndExecute(rowComponent, false, tableRowModel, myColumn);
        }
      }
      else {
        RadViewComponent newRowComponent = null;
        for (int i = rows.size(); i <= myRow; i++) {
          newRowComponent = ModelParser.createComponent(null, tableRowModel);
          ModelParser.addComponent(container, newRowComponent, null);
        }

        execute(myContext, newRowComponent, myComponents, null);
        RadTableLayoutComponent.setCellIndex(editComponent, myColumn);
      }
    }
    else if (myInsertType == GridInsertType.before_h_cell || myInsertType == GridInsertType.after_h_cell) {
      insertInNewRow(tableRowModel, myInsertType == GridInsertType.before_h_cell, myRow, myColumn);
    }
    else if (myInsertType == GridInsertType.before_v_cell || myInsertType == GridInsertType.after_v_cell) {
      int column = myColumn;
      if (myInsertType == GridInsertType.after_v_cell) {
        column++;
      }

      shiftColumns(column);

      RadViewComponent rowComponent = (RadViewComponent)rows.get(myRow);
      if (RadTableRowLayout.is(rowComponent)) {
        insertInRow(rowComponent,
                    myInsertType == GridInsertType.before_v_cell ? gridInfo.components[myRow][column] : null,
                    myInsertType == GridInsertType.after_v_cell,
                    column, column);
      }
      else {
        convertToTableRowAndExecute(rowComponent,
                                    myInsertType == GridInsertType.before_v_cell,
                                    tableRowModel,
                                    column);
      }
    }
    else {
      int column = myColumn;
      if (myInsertType == GridInsertType.corner_top_right || myInsertType == GridInsertType.corner_bottom_right) {
        column++;
      }

      shiftColumns(column);

      insertInNewRow(tableRowModel,
                     myInsertType == GridInsertType.corner_top_left || myInsertType == GridInsertType.corner_top_right,
                     myRow, column);
    }
  }

  private void insertInRow(RadViewComponent rowComponent,
                           @Nullable RadComponent insertBefore,
                           boolean calculateInsert,
                           int startColumn,
                           int column) throws Exception {
    if (calculateInsert) {
      GridInfo gridInfo = getGridInfo();
      RadComponent[] rowComponents = gridInfo.components[myRow];

      for (int i = startColumn; i < rowComponents.length; i++) {
        insertBefore = rowComponents[i];
        if (insertBefore != null) {
          break;
        }
      }
    }

    RadComponent editComponent = myComponents.get(0);
    if (editComponent != insertBefore) {
      execute(myContext, rowComponent, myComponents, (RadViewComponent)insertBefore);
    }

    RadTableLayoutComponent.setCellIndex(editComponent, column);
  }

  private void insertInNewRow(MetaModel tableRowModel, boolean before, int row, int column) throws Exception {
    List<RadComponent> rows = myContainer.getChildren();
    RadComponent insertBefore = null;

    if (before) {
      insertBefore = rows.get(row);
    }
    else if (row + 1 < rows.size()) {
      insertBefore = rows.get(row + 1);
    }

    RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
    ModelParser.addComponent((RadViewComponent)myContainer, newRowComponent, (RadViewComponent)insertBefore);

    execute(myContext, newRowComponent, myComponents, null);
    RadTableLayoutComponent.setCellIndex(myComponents.get(0), column);
  }

  private void convertToTableRowAndExecute(RadViewComponent rowComponent,
                                           boolean insertBefore,
                                           MetaModel tableRowModel,
                                           int column)
    throws Exception {
    RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
    ModelParser.addComponent((RadViewComponent)myContainer, newRowComponent, rowComponent);
    ModelParser.moveComponent(newRowComponent, rowComponent, null);

    RadComponent editComponent = myComponents.get(0);
    if (!insertBefore || editComponent != rowComponent) {
      execute(myContext, newRowComponent, myComponents, insertBefore ? rowComponent : null);
    }

    if (column > 1) {
      RadTableLayoutComponent.setCellIndex(editComponent, column);
    }
  }

  private void shiftColumns(int startColumn) {
    List<RadComponent> rows = myContainer.getChildren();
    RadComponent[][] components = getGridInfo().components;

    for (int i = 0; i < components.length; i++) {
      if (RadTableRowLayout.is(rows.get(i))) {
        RadComponent[] rowComponents = components[i];

        for (int j = startColumn; j < rowComponents.length; j++) {
          RadComponent cellComponent = rowComponents[j];

          if (cellComponent != null) {
            RadTableLayoutComponent.setCellIndex(cellComponent, j + 1);
          }
        }
      }
    }
  }
}