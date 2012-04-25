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

import com.intellij.android.designer.designSurface.layout.grid.GridOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.GridInsertType;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class GridLayoutOperation extends GridOperation {
  public GridLayoutOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  @Override
  public void execute() throws Exception {
    GridInfo gridInfo = getGridInfo();

    RadGridLayoutComponent container = (RadGridLayoutComponent)myContainer;
    RadComponent[][] components = container.getGridComponents(false);
    RadComponent editComponent = myComponents.get(0);
    RadViewComponent nextComponent = getNextComponent(components, myRow, myColumn);

    if (myInsertType == GridInsertType.in_cell) {
      if (myRow <= gridInfo.lastRow && myColumn <= gridInfo.lastColumn) {
        if (myContext.isMove()) {
          Rectangle cellInfo = RadGridLayoutComponent.getCellInfo(editComponent);
          components[cellInfo.y][cellInfo.x] = null;
          components[myRow][myColumn] = editComponent;
        }
        else {
          execute(myContext, container, myComponents, nextComponent);
          RadGridLayoutComponent.setCellIndex(editComponent, myRow, myColumn);
          return;
        }
      }
      else {
        execute(myContext, container, myComponents, null);
        RadGridLayoutComponent.setCellIndex(editComponent, myRow, myColumn);
        return;
      }
    }
    else if (myInsertType == GridInsertType.before_h_cell || myInsertType == GridInsertType.after_h_cell) {
      insertComponent(components, nextComponent, true, myInsertType == GridInsertType.after_h_cell, false, false);
    }
    else if (myInsertType == GridInsertType.before_v_cell || myInsertType == GridInsertType.after_v_cell) {
      insertComponent(components, nextComponent, false, false, true, myInsertType == GridInsertType.after_v_cell);
    }
    else {
      insertComponent(components, nextComponent,
                      true, myInsertType == GridInsertType.corner_bottom_left || myInsertType == GridInsertType.corner_bottom_right,
                      true, myInsertType == GridInsertType.corner_top_right || myInsertType == GridInsertType.corner_bottom_right);
    }

    validateLayoutParams(components);

    if (myContext.isMove()) {
      RadGridLayoutComponent.clearCellSpans(editComponent);
    }
  }

  private void validateLayoutParams(RadComponent[][] components) throws Exception {
    // TODO: Auto-generated method stub
  }

  private RadComponent[][] insertComponent(RadComponent[][] components,
                                           RadViewComponent nextComponent,
                                           boolean insertRow,
                                           boolean afterRow,
                                           boolean insertColumn,
                                           boolean afterColumn) throws Exception {
    int row = myRow;
    if (insertRow && afterRow) {
      row++;
    }

    int column = myColumn;
    if (insertColumn && afterColumn) {
      column++;
    }

    if (insertRow) {
      shiftRowSpan(row);
    }
    if (insertColumn) {
      shiftColumnSpan(column);
    }
    if (insertRow) {
      components = insertRow(components, row);
    }
    if (insertColumn) {
      insertColumn(components, column);
    }

    RadGridLayoutComponent container = (RadGridLayoutComponent)myContainer;
    RadComponent editComponent = myComponents.get(0);

    components[row][column] = editComponent;

    if (myContext.isMove()) {
      Rectangle cellInfo = RadGridLayoutComponent.getCellInfo(editComponent);
      components[cellInfo.y][cellInfo.x] = null;
    }

    if (editComponent != nextComponent) {
      execute(myContext, container, myComponents, nextComponent);
    }

    return components;
  }

  private void shiftRowSpan(int row) {
    GridInfo gridInfo = getGridInfo();
    if (row == gridInfo.lastRow) {
      return;
    }

    RadComponent[] rowComponents = gridInfo.components[row];
    RadComponent[] rowComponents1 = gridInfo.components[row + 1];

    for (int i = 0; i < rowComponents.length; i++) {
      RadComponent cellComponent = rowComponents[i];
      if (cellComponent != null) {
        if (cellComponent == rowComponents1[i]) {
          RadGridLayoutComponent.setSpan(cellComponent, RadGridLayoutComponent.getSpan(cellComponent, true) + 1, true);
        }

        while (i + 1 < rowComponents.length && cellComponent == rowComponents[i + 1]) {
          i++;
        }
      }
    }
  }

  private static RadComponent[][] insertRow(RadComponent[][] components, int row) {
    RadComponent[][] newComponents = new RadComponent[components.length + 1][];

    System.arraycopy(components, 0, newComponents, 0, row);
    System.arraycopy(components, row, newComponents, row + 1, components.length - row);

    newComponents[row] = new RadComponent[components[0].length];

    return newComponents;
  }

  private void shiftColumnSpan(int column) {
    GridInfo gridInfo = getGridInfo();
    if (column == gridInfo.lastColumn) {
      return;
    }

    RadComponent[][] components = gridInfo.components;
    for (int i = 0; i < components.length; i++) {
      RadComponent[] rowComponents = components[i];
      RadComponent cellComponent = rowComponents[column];

      if (cellComponent != null) {
        if (cellComponent == rowComponents[column + 1]) {
          RadGridLayoutComponent.setSpan(cellComponent, RadGridLayoutComponent.getSpan(cellComponent, false) + 1, false);
        }

        while (i + 1 < components.length && cellComponent == components[i + 1][column]) {
          i++;
        }
      }
    }
  }

  private static void insertColumn(RadComponent[][] components, int column) {
    for (int i = 0; i < components.length; i++) {
      RadComponent[] rowComponents = components[i];
      RadComponent[] newRowComponents = new RadComponent[rowComponents.length + 1];

      System.arraycopy(rowComponents, 0, newRowComponents, 0, column);
      System.arraycopy(rowComponents, column, newRowComponents, column + 1, rowComponents.length - column);

      components[i] = newRowComponents;
    }
  }

  @Nullable
  private static RadViewComponent getNextComponent(RadComponent[][] components, int row, int column) {
    RadComponent[] rowComponents = components[row];

    for (int i = column + 1; i < rowComponents.length; i++) {
      RadComponent component = rowComponents[i];
      if (component != null) {
        return (RadViewComponent)component;
      }
    }

    for (int i = row + 1; i < components.length; i++) {
      for (RadComponent component : components[i]) {
        if (component != null) {
          return (RadViewComponent)component;
        }
      }
    }

    return null;
  }
}