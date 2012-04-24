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

    RadViewComponent container = (RadViewComponent)myContainer;
    RadComponent editComponent = myComponents.get(0);

    if (myInsertType == GridInsertType.in_cell) {
      if (myRow <= gridInfo.lastRow && myColumn <= gridInfo.lastColumn) {
        RadComponent insertBefore = getNextComponent(myRow, myColumn);

        if (editComponent != insertBefore) {
          execute(myContext, container, myComponents, (RadViewComponent)insertBefore);
        }
        RadGridLayoutComponent.setCellIndex(editComponent, myRow, myColumn);
      }
      else {
        execute(myContext, container, myComponents, null);
        RadGridLayoutComponent.setCellIndex(editComponent, myRow, myColumn);
        RadGridLayoutComponent.setGridSize(container,
                                           Math.max(myRow + 1, gridInfo.lastRow + 1),
                                           Math.max(myColumn + 1, gridInfo.lastColumn + 1));
      }
    }

    // TODO: Auto-generated method stub
  }

  @Nullable
  private RadComponent getNextComponent(int row, int column) {
    GridInfo gridInfo = getGridInfo();
    RadComponent[][] components = gridInfo.components;

    RadComponent[] rowComponents = components[row];

    for (int i = column + 1; i < rowComponents.length; i++) {
      RadComponent component = rowComponents[i];
      if (component != null) {
        return component;
      }
    }

    for (int i = row + 1; i < components.length; i++) {
      rowComponents = components[i];

      for (int j = 0; i < rowComponents.length; j++) {
        RadComponent component = rowComponents[j];
        if (component != null) {
          return component;
        }
      }
    }

    return null;
  }
}