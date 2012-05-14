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

import com.intellij.android.designer.designSurface.layout.relative.SnapPoint;
import com.intellij.android.designer.designSurface.layout.relative.SnapPointFeedbackHost;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.RectangleFeedback;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RelativeLayoutResizeOperation implements EditOperation {
  public static final String TYPE = "relative_resize";

  private final OperationContext myContext;
  private RadViewComponent myComponent;

  private RectangleFeedback myFeedback;
  private SnapPointFeedbackHost mySnapFeedback;
  private TextFeedback myHorizontalTextFeedback;
  private TextFeedback myVerticalTextFeedback;

  private Rectangle myContainerBounds;
  private Rectangle myBounds;

  private List<SnapPoint> myHorizontalPoints;
  private List<SnapPoint> myVerticalPoints;

  private SnapPoint myHorizontalPoint;
  private SnapPoint myVerticalPoint;

  public RelativeLayoutResizeOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
    myComponent = (RadViewComponent)component;
  }

  @Override
  public void setComponents(List<RadComponent> components) {
  }

  private void createFeedback() {
  }

  @Override
  public void showFeedback() {
    createFeedback();
    // TODO: Auto-generated method stub
  }

  @Override
  public void eraseFeedback() {
    // TODO: Auto-generated method stub
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    // TODO: Auto-generated method stub
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.NORTH_WEST, TYPE,
                                                "Change layout:width x layout:height, top x left alignment"));
    decorator
      .addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.NORTH, TYPE, "Change layout:height, top alignment"));
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.NORTH_EAST, TYPE,
                                                "Change layout:width x layout:height, top x right alignment"));
    decorator
      .addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.EAST, TYPE, "Change layout:width, right alignment"));
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.SOUTH_EAST, TYPE,
                                                "Change layout:width x layout:height, bottom x right alignment"));
    decorator.addPoint(
      new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.SOUTH, TYPE, "Change layout:height, bottom alignment"));
    decorator.addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.SOUTH_WEST, TYPE,
                                                "Change layout:width x layout:height, bottom x left alignment"));
    decorator
      .addPoint(new DirectionResizePoint(ResizeOperation.blue, Color.black, Position.WEST, TYPE, "Change layout:width, left alignment"));
  }
}