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
package com.intellij.android.designer.model.table;

import com.intellij.android.designer.model.agrid.GridInfo;
import com.intellij.android.designer.model.agrid.RadCaptionColumn;
import com.intellij.designer.model.RadComponent;

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
      if (component != null) {
        component.delete();
        rowComponents[myIndex] = null;
      }

      for (int i = myIndex + 1; i < rowComponents.length; i++) {
        RadComponent cellComponent = rowComponents[i];

        if (cellComponent != null) {
          RadTableLayoutComponent.setCellIndex(cellComponent, i - 1);
        }
      }
    }
  }
}