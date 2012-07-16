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
package com.intellij.android.designer.model.layout.table;

import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.grid.RadCaptionColumn;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.IGroupDeleteComponent;
import com.intellij.designer.model.RadComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadCaptionTableColumn extends RadCaptionColumn<RadTableLayoutComponent> implements IGroupDeleteComponent {
  public RadCaptionTableColumn(EditableArea mainArea, RadTableLayoutComponent container, int index, int offset, int width, boolean empty) {
    super(mainArea, container, index, offset, width, empty);
  }

  private void deleteColumn(List<RadComponent> deletedComponents) throws Exception {
    GridInfo info = myContainer.getVirtualGridInfo();
    RadComponent[][] components = info.components;

    for (RadComponent[] rowComponents : components) {
      RadComponent component = rowComponents[myIndex];
      int nextIndex = myIndex + 1;

      if (component != null) {
        while (nextIndex < rowComponents.length && component == rowComponents[nextIndex]) {
          nextIndex++;
        }

        if (myIndex > 0 && component == rowComponents[myIndex - 1]) {
          RadTableLayoutComponent.setCellSpan(component, RadTableLayoutComponent.getCellSpan(component) - 1);
        }
        else {
          Arrays.fill(rowComponents, myIndex, nextIndex, null);

          RadComponent parent = component.getParent();
          component.delete();
          deletedComponents.add(component);

          if (parent.getChildren().isEmpty()) {
            parent.delete();
            deletedComponents.add(parent);
          }
        }
      }
    }
  }

  @Override
  public void delete(List<RadComponent> columns) throws Exception {
    List<RadComponent> deletedComponents = new ArrayList<RadComponent>();

    RadComponent[][] components = myContainer.getGridComponents(false);

    for (RadComponent component : columns) {
      RadCaptionTableColumn column = (RadCaptionTableColumn)component;
      column.deleteColumn(deletedComponents);
    }

    for (int i = 0; i < components.length; i++) {
      RadComponent[] rowComponents = components[i];
      RadComponent[] newRowComponents = new RadComponent[rowComponents.length - columns.size()];
      components[i] = newRowComponents;

      for (int j = 0, index = 0; j < rowComponents.length; j++) {
        boolean add = true;
        for (RadComponent column : columns) {
          if (j == ((RadCaptionTableColumn)column).myIndex) {
            add = false;
            break;
          }
        }
        if (add) {
          newRowComponents[index++] = rowComponents[j];
        }
      }
    }

    for (RadComponent[] rowComponents : components) {
      for (int j = 0; j < rowComponents.length; j++) {
        RadComponent cellComponent = rowComponents[j];
        if (cellComponent != null) {
          RadTableLayoutComponent.setCellIndex(cellComponent, j);
        }
      }
    }

    deselect(deletedComponents);
  }
}