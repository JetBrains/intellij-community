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
package com.intellij.android.designer.model.grid;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.model.RadViewContainer;
import com.intellij.android.designer.model.agrid.GridInfo;
import com.intellij.android.designer.model.agrid.IGridProvider;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Alexander Lobas
 */
public class RadGridLayoutComponent extends RadViewContainer implements IGridProvider {
  private GridInfo myGridInfo;

  @Override
  public void setViewInfo(ViewInfo viewInfo) {
    super.setViewInfo(viewInfo);
    myGridInfo = null;
  }

  @Override
  public GridInfo getGridInfo() {
    if (myGridInfo == null) {
      myGridInfo = new GridInfo();

      try {
        Object viewObject = myViewInfo.getViewObject();
        Class<?> viewClass = viewObject.getClass();

        Method getColumnCount = viewClass.getMethod("getColumnCount");
        myGridInfo.lastColumn = (Integer)getColumnCount.invoke(viewObject) - 1;

        Method getRowCount = viewClass.getMethod("getRowCount");
        myGridInfo.lastRow = (Integer)getRowCount.invoke(viewObject) - 1;

        Field field_horizontalAxis = viewClass.getDeclaredField("horizontalAxis");
        field_horizontalAxis.setAccessible(true);
        Object horizontalAxis = field_horizontalAxis.get(viewObject);

        Class<?> class_Axis = horizontalAxis.getClass();

        Field field_locations = class_Axis.getField("locations");
        field_locations.setAccessible(true);

        myGridInfo.vLines = (int[])field_locations.get(horizontalAxis);

        Field field_verticalAxis = viewClass.getDeclaredField("verticalAxis");
        field_verticalAxis.setAccessible(true);
        Object verticalAxis = field_verticalAxis.get(viewObject);

        myGridInfo.hLines = (int[])field_locations.get(verticalAxis);

        Rectangle bounds = getBounds();

        for (RadComponent child : getChildren()) {
          Rectangle childBounds = child.getBounds();
          myGridInfo.width = Math.max(myGridInfo.width, childBounds.x + childBounds.width - bounds.x);
          myGridInfo.height = Math.max(myGridInfo.height, childBounds.y + childBounds.height - bounds.y);
        }

        if (myGridInfo.vLines != null && myGridInfo.vLines.length > 0) {
          myGridInfo.vLines[myGridInfo.vLines.length - 1] = myGridInfo.width;
        }
        if (myGridInfo.hLines != null && myGridInfo.hLines.length > 0) {
          myGridInfo.hLines[myGridInfo.hLines.length - 1] = myGridInfo.height;
        }
      }
      catch (Throwable e) {
      }
    }
    return myGridInfo;
  }
}