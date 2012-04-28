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
package com.intellij.android.designer.designSurface.layout.actions;

import com.intellij.android.designer.designSurface.layout.grid.GridSelectionDecorator;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class GridLayoutSpanOperation extends LayoutSpanOperation {
  public GridLayoutSpanOperation(OperationContext context, GridSelectionDecorator decorator) {
    super(context, decorator);
  }

  @Override
  public void setComponent(RadComponent component) {
    super.setComponent(component);
    int direction = myContext.getResizeDirection();
    mySpan = RadGridLayoutComponent.getSpan(myComponent, direction == Position.NORTH || direction == Position.SOUTH);
  }

  @Override
  protected String getColumnAttribute(boolean asName) {
    return asName ? "layout:column" : "android:layout_column";
  }

  @Override
  protected String getColumnSpanAttribute(boolean asName) {
    return asName ? "layout:columnSpan" : "android:layout_columnSpan";
  }

  @Override
  protected String getRowAttribute(boolean asName) {
    return asName ? "layout:row" : "android:layout_row";
  }

  @Override
  protected String getRowSpanAttribute(boolean asName) {
    return asName ? "layout:rowSpan" : "android:layout_rowSpan";
  }

  @Override
  protected Point getCellInfo() {
    return RadGridLayoutComponent.getCellInfo(myComponent).getLocation();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(GridSelectionDecorator decorator) {
    decorator.addPoint(new SpanPoint(COLOR,
                                     Color.black,
                                     Position.WEST,
                                     TYPE,
                                     "Change layout:column x layout:columnSpan",
                                     decorator)); // left

    decorator.addPoint(new SpanPoint(COLOR,
                                     Color.black,
                                     Position.EAST,
                                     TYPE,
                                     "Change layout:columnSpan",
                                     decorator).move(1, 0.75)); // right

    decorator.addPoint(new SpanPoint(COLOR,
                                     Color.black,
                                     Position.NORTH,
                                     TYPE,
                                     "Change layout:row x layout:rowSpan",
                                     decorator)); // top

    decorator.addPoint(new SpanPoint(COLOR,
                                     Color.black,
                                     Position.SOUTH,
                                     TYPE,
                                     "Change layout:rowSpan",
                                     decorator).move(0.75, 1)); // bottom
  }
}