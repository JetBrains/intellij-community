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
package com.intellij.android.designer.model.layout.grid;

import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.layout.GridLayoutOperation;
import com.intellij.android.designer.designSurface.layout.actions.GridLayoutSpanOperation;
import com.intellij.android.designer.designSurface.layout.actions.LayoutSpanOperation;
import com.intellij.android.designer.designSurface.layout.actions.ResizeOperation;
import com.intellij.android.designer.designSurface.layout.caption.GridHorizontalCaptionOperation;
import com.intellij.android.designer.designSurface.layout.caption.GridVerticalCaptionOperation;
import com.intellij.android.designer.designSurface.layout.grid.GridDecorator;
import com.intellij.android.designer.designSurface.layout.grid.GridSelectionDecorator;
import com.intellij.android.designer.model.RadViewLayout;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.android.designer.model.grid.GridInfo;
import com.intellij.android.designer.model.layout.actions.AllGravityAction;
import com.intellij.android.designer.model.layout.actions.OrientationAction;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadGridLayout extends RadViewLayoutWithData implements ILayoutDecorator, ICaption, ICaptionDecorator {
  private static final String[] LAYOUT_PARAMS = {"GridLayout_Layout", "ViewGroup_MarginLayout"};

  private GridDecorator myGridDecorator;
  private GridSelectionDecorator mySelectionDecorator;

  @NotNull
  @Override
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
      return new GridLayoutOperation(myContainer, context);
    }
    if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
    if (context.is(LayoutSpanOperation.TYPE)) {
      return new GridLayoutSpanOperation(context, mySelectionDecorator);
    }
    return null;
  }

  private StaticDecorator getGridDecorator() {
    if (myGridDecorator == null) {
      myGridDecorator = new GridDecorator(myContainer);
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
        if (component.getParent() == myContainer) {
          decorators.add(getGridDecorator());
          return;
        }
      }
      super.addStaticDecorators(decorators, selection);
    }
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new GridSelectionDecorator(JBColor.RED, 1) {
        @Override
        public Rectangle getCellBounds(Component layer, RadComponent component) {
          try {
            RadGridLayoutComponent parent = (RadGridLayoutComponent)component.getParent();
            GridInfo gridInfo = parent.getGridInfo();
            Rectangle cellInfo = RadGridLayoutComponent.getCellInfo(component);

            return calculateBounds(layer, gridInfo, parent, component, cellInfo.y, cellInfo.x, cellInfo.height, cellInfo.width);
          }
          catch (Throwable e) {
            return new Rectangle();
          }
        }
      };
    }

    mySelectionDecorator.clear();
    if (selection.size() == 1) {
      GridLayoutSpanOperation.points(mySelectionDecorator);
    }
    ResizeOperation.width(mySelectionDecorator);
    ResizeOperation.height(mySelectionDecorator);

    return mySelectionDecorator;
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
    if (selection.get(selection.size() - 1) != myContainer) {
      return;
    }
    for (RadComponent component : selection) {
      if (!(component.getLayout() instanceof RadGridLayout)) {
        return;
      }
    }

    createOrientationAction(designer, actionGroup, shortcuts, selection);
  }

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    if (selection.get(selection.size() - 1).getParent() != myContainer) {
      return;
    }
    for (RadComponent component : selection) {
      if (!(component.getParent() instanceof RadGridLayoutComponent)) {
        return;
      }
    }

    createOrientationAction(designer, actionGroup, shortcuts, Arrays.asList(myContainer));
    actionGroup.add(new AllGravityAction(designer, selection));
  }

  private static void createOrientationAction(DesignerEditorPanel designer,
                                              DefaultActionGroup actionGroup,
                                              JComponent shortcuts,
                                              List<RadComponent> components) {
    boolean override = false;
    Iterator<RadComponent> I = components.iterator();
    boolean horizontal = ((RadGridLayoutComponent)I.next()).isHorizontal();

    while (I.hasNext()) {
      boolean next = ((RadGridLayoutComponent)I.next()).isHorizontal();
      if (horizontal != next) {
        override = true;
        break;
      }
    }

    actionGroup.add(new OrientationAction(designer, components, horizontal, override));
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Caption
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public ICaption getCaption(RadComponent component) {
    if (myContainer == component && myContainer.getParent().getLayout() instanceof ICaptionDecorator) {
      return null;
    }
    if (myContainer.getChildren().isEmpty()) {
      return null;
    }
    return this;
  }

  @NotNull
  @Override
  public List<RadComponent> getCaptionChildren(EditableArea mainArea, boolean horizontal) {
    RadGridLayoutComponent container = getGridComponent();
    GridInfo gridInfo = container.getGridInfo();
    List<RadComponent> components = new ArrayList<RadComponent>();

    if (horizontal) {
      int[] lines = gridInfo.vLines;
      boolean[] emptyColumns = gridInfo.emptyColumns;

      for (int i = 0; i < lines.length - 1; i++) {
        components.add(new RadCaptionGridColumn(mainArea,
                                                container,
                                                i,
                                                lines[i],
                                                lines[i + 1] - lines[i],
                                                emptyColumns[i]));
      }
    }
    else {
      int[] lines = gridInfo.hLines;
      boolean[] emptyRows = gridInfo.emptyRows;

      for (int i = 0; i < lines.length - 1; i++) {
        components.add(new RadCaptionGridRow(mainArea,
                                             container,
                                             i,
                                             lines[i],
                                             lines[i + 1] - lines[i],
                                             emptyRows[i]));
      }
    }

    return components;
  }

  private RadGridLayoutComponent getGridComponent() {
    return (RadGridLayoutComponent)myContainer;
  }

  private RadLayout myCaptionColumnLayout;
  private RadLayout myCaptionRowLayout;

  @NotNull
  @Override
  public RadLayout getCaptionLayout(final EditableArea mainArea, boolean horizontal) {
    if (horizontal) {
      if (myCaptionColumnLayout == null) {
        myCaptionColumnLayout = new RadViewLayout() {
          @Override
          public EditOperation processChildOperation(OperationContext context) {
            if (context.isMove()) {
              return new GridHorizontalCaptionOperation(getGridComponent(), myContainer, context, mainArea);
            }
            return null;
          }
        };
      }
      return myCaptionColumnLayout;
    }

    if (myCaptionRowLayout == null) {
      myCaptionRowLayout = new RadViewLayout() {
        @Override
        public EditOperation processChildOperation(OperationContext context) {
          if (context.isMove()) {
            return new GridVerticalCaptionOperation(getGridComponent(), myContainer, context, mainArea);
          }
          return null;
        }
      };
    }
    return myCaptionRowLayout;
  }
}