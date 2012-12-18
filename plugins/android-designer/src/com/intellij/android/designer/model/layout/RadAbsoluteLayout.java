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
package com.intellij.android.designer.model.layout;

import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.layout.AbsoluteLayoutOperation;
import com.intellij.android.designer.designSurface.layout.actions.ResizeOperation;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadAbsoluteLayout extends RadViewLayoutWithData {
  private static final String[] LAYOUT_PARAMS = {"AbsoluteLayout_Layout", "ViewGroup_Layout"};

  private ResizeSelectionDecorator mySelectionDecorator;

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.isCreate() || context.isPaste() || context.isAdd() || context.isMove()) {
      if (context.isTree()) {
        if (TreeEditOperation.isTarget(myContainer, context)) {
          return new TreeDropToOperation(myContainer, context);
        }
        return null;
      }
      return new AbsoluteLayoutOperation(myContainer, context);
    }
    if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
    return null;
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new ResizeSelectionDecorator(JBColor.RED, 1);
      ResizeOperation.points(mySelectionDecorator);
    }
    return mySelectionDecorator;
  }
}