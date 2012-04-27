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

import com.intellij.android.designer.model.layout.grid.RadCaptionGridRow;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class GridVerticalCaptionOperation extends VerticalCaptionFlowBaseOperation<RadGridLayoutComponent> {
  public GridVerticalCaptionOperation(RadGridLayoutComponent mainContainer,
                                      RadComponent container,
                                      OperationContext context,
                                      EditableArea mainArea) {
    super(mainContainer, container, context, mainArea);
  }

  @Override
  protected int getMainFeedbackWidth(FeedbackLayer layer, int mainXLocation) {
    return myMainContainer.getGridInfo().width;
  }

  @Override
  protected void execute(@Nullable RadComponent insertBefore) throws Exception {
    RadComponent[][] components = myMainContainer.getGridComponents(false);

    for (RadComponent component : myComponents) {
      component.removeFromParent();
      myContainer.add(component, insertBefore);
    }

    List<RadComponent> rows = myContainer.getChildren();
    int size = rows.size();
    for (int i = 0; i < size; i++) {
      int index = ((RadCaptionGridRow)rows.get(i)).getIndex();
      RadComponent[] rowComponents = components[i];

      for (int j = 0; j < rowComponents.length; j++) {
        RadComponent cellComponent = components[index][j];
        if (cellComponent != null) {
          RadGridLayoutComponent.setCellIndex(cellComponent, i, j, true, false);
        }
      }
    }
  }
}