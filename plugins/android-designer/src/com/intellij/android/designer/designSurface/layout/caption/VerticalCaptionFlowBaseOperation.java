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

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.table.RadCaptionTableRow;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.FlowBaseOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class VerticalCaptionFlowBaseOperation extends FlowBaseOperation {
  private final RadTableLayoutComponent myTableComponent;
  private final OperationContext myMainContext;
  private final FlowBaseOperation myMainOperation;

  public VerticalCaptionFlowBaseOperation(RadTableLayoutComponent tableComponent,
                                          RadComponent container,
                                          OperationContext context,
                                          EditableArea mainArea) {
    super(container, context, false);
    myTableComponent = tableComponent;
    myMainContext = new OperationContext(context.getType());
    myMainContext.setArea(mainArea);

    myMainOperation = new FlowBaseOperation(tableComponent, myMainContext, false) {
      @Override
      protected void execute(@Nullable RadComponent insertBefore) throws Exception {
      }
    };
  }

  @Override
  public void setComponents(List<RadComponent> components) {
    super.setComponents(components);
    myMainContext.setComponents(getRowComponents(components));
  }

  @Override
  public void showFeedback() {
    super.showFeedback();
    myMainContext.setLocation(SwingUtilities.convertPoint(myContext.getArea().getFeedbackLayer(),
                                                          myContext.getLocation(),
                                                          myMainContext.getArea().getFeedbackLayer()));
    myMainOperation.showFeedback();
  }

  @Override
  public void eraseFeedback() {
    super.eraseFeedback();
    myMainOperation.eraseFeedback();
  }

  @Override
  protected void execute(@Nullable RadComponent insertBefore) throws Exception {
    for (RadComponent component : myComponents) {
      component.removeFromParent();
      myContainer.add(component, insertBefore);
    }

    AbstractEditOperation.execute(myMainContext,
                                  myTableComponent,
                                  getRowComponents(myComponents),
                                  getRowComponent(insertBefore));
  }

  private static List<RadComponent> getRowComponents(List<RadComponent> components) {
    List<RadComponent> rowComponents = new ArrayList<RadComponent>();
    for (RadComponent component : components) {
      rowComponents.add(((RadCaptionTableRow)component).getComponent());
    }
    return rowComponents;
  }

  @Nullable
  private static RadViewComponent getRowComponent(@Nullable RadComponent component) {
    RadCaptionTableRow row = (RadCaptionTableRow)component;
    return row == null ? null : row.getComponent();
  }
}