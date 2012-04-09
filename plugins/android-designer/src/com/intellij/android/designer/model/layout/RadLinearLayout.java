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
import com.intellij.android.designer.designSurface.layout.FlowStaticDecorator;
import com.intellij.android.designer.designSurface.layout.LinearLayoutOperation;
import com.intellij.android.designer.designSurface.layout.ResizeOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadLinearLayout extends RadViewLayoutWithData implements ILayoutDecorator {
  private static final String[] LAYOUT_PARAMS = {"LinearLayout_Layout", "ViewGroup_MarginLayout"};

  private final ResizeSelectionDecorator mySelectionDecorator = new ResizeSelectionDecorator(Color.red, 1) {
    @Override
    protected boolean visible(RadComponent component, ResizePoint point) {
      // XXX
      return true;
    }
  };
  private FlowStaticDecorator myLineDecorator;

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.isCreate() || context.isPaste() || context.isAdd() || context.isMove()) {
      if (context.isTree()) {
        return new TreeDropToOperation(myContainer, context);
      }
      return new LinearLayoutOperation((RadViewComponent)myContainer, context, isHorizontal());
    }
    else if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
    return null;
  }

  private StaticDecorator getLineDecorator() {
    if (myLineDecorator == null) {
      myLineDecorator = new FlowStaticDecorator(myContainer) {
        @Override
        protected boolean isHorizontal() {
          return RadLinearLayout.this.isHorizontal();
        }
      };
    }
    return myLineDecorator;
  }

  private boolean isHorizontal() {
    return !"vertical".equals(((RadViewComponent)myContainer).getTag().getAttributeValue("android:orientation"));
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    if (selection.contains(myContainer)) {
      if (!(myContainer.getParent().getLayout() instanceof ILayoutDecorator)) {
        decorators.add(getLineDecorator());
      }
    }
    else {
      for (RadComponent component : selection) {
        if (component.getParent() == myContainer) {
          decorators.add(getLineDecorator());
          return;
        }
      }
      super.addStaticDecorators(decorators, selection);
    }
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    mySelectionDecorator.clear();
    if (selection.size() == 1) {
      // XXX
    }
    ResizeOperation.points(mySelectionDecorator);
    return mySelectionDecorator;
  }

  @Override
  public void addContainerSelectionActions(DesignerEditorPanel designer,
                                           DefaultActionGroup actionGroup,
                                           JComponent shortcuts,
                                           List<RadComponent> selection) {
    super.addContainerSelectionActions(designer, actionGroup, shortcuts, selection); // TODO: Auto-generated method stub
  }

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    super.addSelectionActions(designer, actionGroup, shortcuts, selection); // TODO: Auto-generated method stub
  }
}