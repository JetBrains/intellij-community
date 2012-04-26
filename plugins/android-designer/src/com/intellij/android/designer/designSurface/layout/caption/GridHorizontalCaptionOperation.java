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
package com.intellij.android.designer.designSurface.layout.caption;

import com.intellij.android.designer.designSurface.layout.GridLayoutOperation;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.RadCaptionColumn;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class GridHorizontalCaptionOperation extends HorizontalCaptionFlowBaseOperation<RadGridLayoutComponent> {
  public GridHorizontalCaptionOperation(RadGridLayoutComponent mainContainer,
                                        RadComponent container,
                                        OperationContext context,
                                        EditableArea mainArea) {
    super(mainContainer, container, context, mainArea);
  }

  @Override
  protected void execute(@Nullable RadComponent insertBefore) throws Exception {
    RadComponent[][] components = myMainContainer.getGridComponents(false);

    for (int i = 0; i < components.length; i++) {
      RadComponent[] rowComponents = components[i];

      List<RadViewComponent> editComponents = new ArrayList<RadViewComponent>();
      int count = 0;
      for (RadComponent component : myComponents) {
        int column = ((RadCaptionColumn)component).getIndex();
        RadViewComponent editComponent = (RadViewComponent)rowComponents[column];
        editComponents.add(editComponent);
        if (editComponent != null) {
          RadGridLayoutComponent.clearCellSpans(editComponent);
          count++;
        }
      }

      if (count > 0) {
        RadViewComponent insertBeforeColumn = null;
        if (insertBefore != null) {
          int column = ((RadCaptionColumn)insertBefore).getIndex();
          for (int j = column; j < rowComponents.length; j++) {
            insertBeforeColumn = (RadViewComponent)rowComponents[j];
            if (insertBeforeColumn != null) {
              if (!editComponents.isEmpty() && insertBeforeColumn == editComponents.get(0)) {
                editComponents.remove(0);
                insertBeforeColumn = null;
                continue;
              }
              break;
            }
          }
        }

        if (insertBefore == null || insertBeforeColumn != null) {
          for (RadViewComponent component : editComponents) {
            if (component != insertBeforeColumn) {
              ModelParser.moveComponent(myMainContainer, component, insertBeforeColumn);
            }
          }
        }
      }

      List<RadComponent> rowList = new ArrayList<RadComponent>();
      Collections.addAll(rowList, rowComponents);
      rowList.removeAll(editComponents);

      if (insertBefore == null) {
        rowList.addAll(editComponents);
      }
      else {
        rowList.addAll(((RadCaptionColumn)insertBefore).getIndex(), editComponents);
      }

      for (RadViewComponent component : editComponents) {
        Rectangle cellIndex = RadGridLayoutComponent.getCellInfo(component);
        GridInfo.setNull(components, null, cellIndex.y, cellIndex.y + cellIndex.height, cellIndex.x, cellIndex.x + cellIndex.width);
      }

      components[i] = rowList.toArray(new RadComponent[rowList.size()]);
    }

    for (RadComponent component : myComponents) {
      component.removeFromParent();
      myContainer.add(component, insertBefore);
    }

    GridLayoutOperation.validateLayoutParams(components);
  }
}