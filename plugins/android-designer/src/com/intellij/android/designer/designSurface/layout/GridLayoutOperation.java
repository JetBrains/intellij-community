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
  protected int getMovedIndex(boolean row) {
    RadComponent movedComponent = myContext.getComponents().get(0);
    Rectangle movedCellInfo = RadGridLayoutComponent.getCellInfo(movedComponent);

    return row ? movedCellInfo.y : movedCellInfo.x;
  }

  @Override
  protected boolean isSingleMovedAxis(boolean row) {
    RadComponent movedComponent = myContext.getComponents().get(0);
    Rectangle movedCellInfo = RadGridLayoutComponent.getCellInfo(movedComponent);

    if (row) {
      for (int i = 0; i < movedCellInfo.height; i++) {
        if (getSizeInRow(movedCellInfo.y + i, movedComponent) > 0) {
          return false;
        }
      }
    }
    else {
      GridInfo gridInfo = getGridInfo();
      for (int i = 0; i < movedCellInfo.width; i++) {
        if (getSizeInColumn(movedCellInfo.x + i, gridInfo.columnCount, movedComponent) > 0) {
          return false;
        }
      }
    }

    return true;
  }

  @Override
  public void execute() throws Exception {
    GridInfo gridInfo = getGridInfo();

    RadGridLayoutComponent container = (RadGridLayoutComponent)myContainer;
    RadComponent[][] components = container.getGridComponents(false);
    RadComponent editComponent = myComponents.get(0);

    if (myInsertType == GridInsertType.in_cell && (myRow >= gridInfo.rowCount || myColumn >= gridInfo.columnCount)) {
      execute(myContext, container, myComponents, null);
      RadGridLayoutComponent.setCellIndex(editComponent, myRow, myColumn, true, true);
      return;
    }

    RadViewComponent nextComponent = getNextComponent(components, myRow, myColumn);

    if (myInsertType == GridInsertType.in_cell) {
      if (!myContext.isMove()) {
        execute(myContext, container, myComponents, nextComponent);
        RadGridLayoutComponent.setCellIndex(editComponent, myRow, myColumn, true, true);
        return;
      }

      components = insertComponent(components, nextComponent, false, false, false, false);
    }
    else if (myInsertType == GridInsertType.before_h_cell || myInsertType == GridInsertType.after_h_cell) {
      components = insertComponent(components, nextComponent,
                                   true, myInsertType == GridInsertType.after_h_cell,
                                   false, false);
    }
    else if (myInsertType == GridInsertType.before_v_cell || myInsertType == GridInsertType.after_v_cell) {
      components = insertComponent(components, nextComponent, false, false,
                                   true, myInsertType == GridInsertType.after_v_cell);
    }
    else {
      components = insertComponent(components, nextComponent,
                                   true,
                                   myInsertType == GridInsertType.corner_bottom_left || myInsertType == GridInsertType.corner_bottom_right,
                                   true,
                                   myInsertType == GridInsertType.corner_top_right || myInsertType == GridInsertType.corner_bottom_right);
    }

    validateLayoutParams(components);
  }

  public static void validateLayoutParams(RadComponent[][] components) throws Exception {
    for (int i = 0; i < components.length; i++) {
      RadComponent[] rowComponents = components[i];
      for (int j = 0; j < rowComponents.length; j++) {
        RadComponent cellComponent = rowComponents[j];
        if (cellComponent != null) {
          RadGridLayoutComponent.setCellIndex(cellComponent, i, j, false, false);
        }
      }
    }
  }

  private RadComponent[][] insertComponent(RadComponent[][] components,
                                           RadViewComponent nextComponent,
                                           boolean insertRow,
                                           boolean afterRow,
                                           boolean insertColumn,
                                           boolean afterColumn) throws Exception {
    RadComponent editComponent = myComponents.get(0);

    if (myContext.isMove()) {
      Rectangle cellInfo = RadGridLayoutComponent.getCellInfo(editComponent);
      if (cellInfo.y < components.length && cellInfo.x < components[cellInfo.y].length) {
        components[cellInfo.y][cellInfo.x] = null;
      }
      RadGridLayoutComponent.clearCellSpans(editComponent);
    }

    int row = myRow;
    if (insertRow && afterRow) {
      row++;
    }

    int column = myColumn;
    if (insertColumn && afterColumn) {
      column++;
    }

    GridInfo gridInfo = getGridInfo();

    if (insertRow) {
      shiftRowSpan(gridInfo, row, 1);
    }
    if (insertColumn) {
      shiftColumnSpan(gridInfo, column, 1);
    }
    if (insertRow) {
      components = insertRow(components, row);
    }
    if (insertColumn) {
      insertColumn(components, column);
    }

    components[row][column] = editComponent;

    if (editComponent != nextComponent) {
      execute(myContext, (RadGridLayoutComponent)myContainer, myComponents, nextComponent);
    }

    return components;
  }

  public static void shiftRowSpan(GridInfo gridInfo, int row, int inc) {
    if (row >= gridInfo.rowCount - 1) {
      return;
    }

    RadComponent[] rowComponents = gridInfo.components[row];
    RadComponent[] rowComponents1 = gridInfo.components[row + 1];

    for (int i = 0; i < rowComponents.length; i++) {
      RadComponent cellComponent = rowComponents[i];
      if (cellComponent != null) {
        if (cellComponent == rowComponents1[i]) {
          RadGridLayoutComponent.setSpan(cellComponent, RadGridLayoutComponent.getSpan(cellComponent, true) + inc, true);
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

  public static void shiftColumnSpan(GridInfo gridInfo, int column, int inc) {
    if (column >= gridInfo.columnCount - 1) {
      return;
    }

    RadComponent[][] components = gridInfo.components;
    for (int i = 0; i < components.length; i++) {
      RadComponent[] rowComponents = components[i];
      RadComponent cellComponent = rowComponents[column];

      if (cellComponent != null) {
        if (cellComponent == rowComponents[column + 1]) {
          RadGridLayoutComponent.setSpan(cellComponent, RadGridLayoutComponent.getSpan(cellComponent, false) + inc, false);
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
  public static RadViewComponent getNextComponent(RadComponent[][] components, int row, int column) {
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