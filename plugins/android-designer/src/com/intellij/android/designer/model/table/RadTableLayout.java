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

import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.layout.TableLayoutDecorator;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayout;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadTableLayout extends RadViewLayoutWithData implements ILayoutDecorator, ICaption, ICaptionDecorator {
  private static final String[] LAYOUT_PARAMS = {"", "LinearLayout_Layout", "ViewGroup_MarginLayout"};

  private TableLayoutDecorator myGridDecorator;

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
      // XXX
    }
    // XXX
    return null;
  }

  private StaticDecorator getGridDecorator() {
    if (myGridDecorator == null) {
      myGridDecorator = new TableLayoutDecorator(myContainer);
    }
    return myGridDecorator;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    if (selection.contains(myContainer)) {
      if (!(myContainer.getParent().getLayout() instanceof ILayoutDecorator)) {
        decorators.add(getGridDecorator());
      }
    }
    else {
      for (RadComponent component : selection) {
        RadComponent parent = component.getParent();
        if (parent == myContainer || (parent != null && parent.getParent() == myContainer)) {
          decorators.add(getGridDecorator());
          return;
        }
      }
      super.addStaticDecorators(decorators, selection);
    }
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    return super.getChildSelectionDecorator(component, selection); // TODO: Auto-generated method stub
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Actions
  //
  //////////////////////////////////////////////////////////////////////////////////////////

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

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Caption
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public ICaption getCaption(RadComponent component) {
    return myContainer == component && myContainer.getParent().getLayout() instanceof ICaptionDecorator ? null : this;
  }

  @Override
  @NotNull
  public RadLayout getCaptionLayout(EditableArea mainArea, boolean horizontal) {
    return RadViewLayout.INSTANCE;
  }

  @Override
  @NotNull
  public List<RadComponent> getCaptionChildren(EditableArea mainArea, boolean horizontal) {
    RadTableLayoutComponent container = (RadTableLayoutComponent)myContainer;
    List<RadComponent> children = container.getChildren();
    List<RadComponent> components = new ArrayList<RadComponent>();

    if (horizontal) {
      if (children.isEmpty()) {
        components.add(new RadCaptionTableColumn(container, 0, container.getBounds().width, mainArea));
      }
      else {
        int columnOffset = 0;
        for (int columnWidth : container.getColumnWidths()) {
          components.add(new RadCaptionTableColumn(container, columnOffset, Math.max(columnWidth, 2), mainArea));

          if (columnWidth > 0) {
            columnOffset += columnWidth;
          }
        }
      }
    }
    else if (children.isEmpty()) {
      components.add(new RadCaptionTableRow(container, container, mainArea));
    }
    else {
      for (RadComponent component : children) {
        components.add(new RadCaptionTableRow(container, (RadViewComponent)component, mainArea));
      }
    }

    return components;
  }
}