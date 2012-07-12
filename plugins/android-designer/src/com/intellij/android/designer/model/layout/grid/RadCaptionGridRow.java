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
package com.intellij.android.designer.model.layout.grid;

import com.intellij.android.designer.designSurface.layout.GridLayoutOperation;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.RadCaptionRow;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.IGroupDeleteComponent;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadCaptionGridRow extends RadCaptionRow<RadGridLayoutComponent> implements IGroupDeleteComponent {
  public RadCaptionGridRow(EditableArea mainArea, RadGridLayoutComponent container, int index, int offset, int width, boolean empty) {
    super(mainArea, container, index, offset, width, empty);
  }

  @Override
  public void delete(List<RadComponent> rows) throws Exception {
    List<RadComponent> deletedComponents = new ArrayList<RadComponent>();

    GridInfo gridInfo = myContainer.getVirtualGridInfo();
    RadComponent[][] components = myContainer.getGridComponents(false);

    for (RadComponent row : rows) {
      delete(deletedComponents, gridInfo, components, (RadCaptionGridRow)row);
    }

    RadComponent[][] newComponents = new RadComponent[components.length - rows.size()][];

    for (int i = 0, index = 0; i < components.length; i++) {
      boolean add = true;
      for (RadComponent row : rows) {
        if (i == ((RadCaptionGridRow)row).myIndex) {
          add = false;
          break;
        }
      }
      if (add) {
        newComponents[index++] = components[i];
      }
    }

    GridLayoutOperation.validateLayoutParams(newComponents);

    deselect(deletedComponents);
  }

  private static void delete(List<RadComponent> deletedComponents, GridInfo gridInfo, RadComponent[][] components, RadCaptionGridRow row)
    throws Exception {
    if (row.myIndex > 0) {
      GridLayoutOperation.shiftRowSpan(gridInfo, row.myIndex - 1, -1);
    }

    for (RadComponent component : components[row.myIndex]) {
      if (component != null) {
        Rectangle cellIndex = RadGridLayoutComponent.getCellInfo(component);
        GridInfo.setNull(components, gridInfo.components, cellIndex.y, cellIndex.y + cellIndex.height, cellIndex.x,
                         cellIndex.x + cellIndex.width);
        component.delete();
        deletedComponents.add(component);
      }
    }
  }
}