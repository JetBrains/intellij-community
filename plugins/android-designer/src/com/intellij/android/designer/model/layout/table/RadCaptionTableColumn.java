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
import com.intellij.designer.model.RadComponent;

import java.util.Arrays;

/**
 * @author Alexander Lobas
 */
public class RadCaptionTableColumn extends RadCaptionColumn<RadTableLayoutComponent> {
  public RadCaptionTableColumn(RadTableLayoutComponent container, int index, int offset, int width, boolean empty) {
    super(container, index, offset, width, empty);
  }

  @Override
  public void delete() throws Exception {
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
          if (parent.getChildren().isEmpty()) {
            parent.delete();
          }
        }
      }

      for (int i = nextIndex; i < rowComponents.length; i++) {
        RadComponent cellComponent = rowComponents[i];
        if (cellComponent != null) {
          RadTableLayoutComponent.setCellIndex(cellComponent, i - 1);

          while (i + 1 < rowComponents.length && cellComponent == rowComponents[i + 1]) {
            i++;
          }
        }
      }
    }
  }
}