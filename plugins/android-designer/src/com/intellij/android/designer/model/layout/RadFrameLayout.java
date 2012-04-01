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
import com.intellij.android.designer.designSurface.layout.*;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadFrameLayout extends RadViewLayoutWithData {
  private static final String[] LAYOUT_PARAMS = {"FrameLayout_Layout", "ViewGroup_MarginLayout"};

  @Override
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.isCreate() || context.isPaste() || context.isAdd() || context.isMove()) {
      if (context.isTree()) {
        return new TreeDropToOperation(myContainer, context);
      }
      return new FrameLayoutOperation((RadViewComponent)myContainer, context);
    }
    else if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
    else if (context.is(FrameLayoutMarginOperation.TYPE)) {
      return new FrameLayoutMarginOperation(context);
    }
    return null;
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    ResizeSelectionDecorator decorator = new ResizeSelectionDecorator(Color.red, 1) {
      @Override
      protected boolean visible(RadComponent component, ResizePoint point) {
        if (point.getType() == FrameLayoutMarginOperation.TYPE) {
          return FrameLayoutMarginOperation.visible(component, (DirectionResizePoint)point);
        }
        return true;
      }
    };
    if (selection.size() == 1) {
      FrameLayoutMarginOperation.points(decorator);
    }
    ResizeOperation.points(decorator);
    return decorator;
  }

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    super.addSelectionActions(designer, actionGroup, shortcuts, selection); // TODO: Auto-generated method stub
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static Pair<Gravity, Gravity> gravity(RadComponent component) {
    String value = ((RadViewComponent)component).getTag().getAttributeValue("android:layout_gravity");
    int flags = Gravity.getFlags(value);

    Gravity horizontal = Gravity.left;
    if ((flags & Gravity.LEFT) != 0) {
      horizontal = Gravity.left;
    }
    else if ((flags & Gravity.CENTER_HORIZONTAL) != 0) {
      horizontal = Gravity.center;
    }
    else if ((flags & Gravity.RIGHT) != 0) {
      horizontal = Gravity.right;
    }

    Gravity vertical = Gravity.top;
    if ((flags & Gravity.TOP) != 0) {
      vertical = Gravity.top;
    }
    else if ((flags & Gravity.CENTER_VERTICAL) != 0) {
      vertical = Gravity.center;
    }
    else if ((flags & Gravity.BOTTOM) != 0) {
      vertical = Gravity.bottom;
    }

    return new Pair<Gravity, Gravity>(horizontal, vertical);
  }
}