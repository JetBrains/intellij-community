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
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.util.ArrayUtil;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TableLayoutSpanOperation extends LayoutSpanOperation {
  public TableLayoutSpanOperation(OperationContext context, GridSelectionDecorator decorator) {
    super(context, decorator);
  }

  @Override
  public void setComponent(RadComponent component) {
    super.setComponent(component);
    mySpan = RadTableLayoutComponent.getCellSpan(myComponent);
  }

  @Override
  protected String getColumnAttribute(boolean asName) {
    return asName ? "layout:column" : "android:layout_column";
  }

  @Override
  protected String getColumnSpanAttribute(boolean asName) {
    return asName ? "layout:span" : "android:layout_span";
  }

  @Override
  protected String getRowAttribute(boolean asName) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected String getRowSpanAttribute(boolean asName) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected RadComponent getContainer() {
    return myComponent.getParent().getParent();
  }

  @Override
  protected Point getCellInfo() {
    RadTableLayoutComponent tableComponent = (RadTableLayoutComponent)myComponent.getParent().getParent();
    GridInfo gridInfo = tableComponent.getVirtualGridInfo();
    int row = tableComponent.getChildren().indexOf(myComponent.getParent());
    int column = ArrayUtil.indexOf(gridInfo.components[row], myComponent);

    return new Point(column, row);
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
                                     "Change layout:column x layout:span",
                                     decorator)); // left

    decorator.addPoint(new SpanPoint(COLOR,
                                     Color.black,
                                     Position.EAST,
                                     TYPE,
                                     "Change layout:span",
                                     decorator)); // right
  }
}