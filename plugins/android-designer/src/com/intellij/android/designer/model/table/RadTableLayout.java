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

import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.layout.ResizeOperation;
import com.intellij.android.designer.designSurface.layout.TableLayoutDecorator;
import com.intellij.android.designer.designSurface.layout.TableLayoutOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayout;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadTableLayout extends RadViewLayoutWithData implements ILayoutDecorator, ICaption, ICaptionDecorator {
  private static final String[] LAYOUT_PARAMS = {"", "LinearLayout_Layout", "ViewGroup_MarginLayout"};

  private TableLayoutDecorator myGridDecorator;
  private ResizeSelectionDecorator myResizeDecorator;

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
      return new TableLayoutOperation(myContainer, context);
    }
    if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
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
    if (RadTableRowLayout.is(component)) {
      return super.getChildSelectionDecorator(component, selection);
    }

    if (myResizeDecorator == null) {
      myResizeDecorator = new ResizeSelectionDecorator(Color.red, 1);
      ResizeOperation.height(myResizeDecorator);
    }
    return myResizeDecorator;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Caption
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public ICaption getCaption(RadComponent component) {
    if (myContainer == component) {
      // skip for caption parent, example: ...( Caption( TableLayout ) )
      RadComponent parent = myContainer.getParent();
      if (parent.getLayout() instanceof ICaptionDecorator) {
        return null;
      }

      // skip for caption parent.parent, example: ...( TableLayout( TableRowLayout( TableLayout ) ) )
      parent = parent.getParent();
      if (parent != null && parent.getLayout() instanceof ICaptionDecorator) {
        return null;
      }
    }
    if (myContainer.getChildren().isEmpty()) {
      return null;
    }
    return this;
  }

  @Override
  @NotNull
  public List<RadComponent> getCaptionChildren(EditableArea mainArea, boolean horizontal) {
    RadTableLayoutComponent container = (RadTableLayoutComponent)myContainer;
    List<RadComponent> children = container.getChildren();
    List<RadComponent> components = new ArrayList<RadComponent>();

    if (horizontal) {
      GridInfo gridInfo = container.getGridInfo();
      int[] lines = gridInfo.vLines;
      boolean[] emptyColumns = gridInfo.emptyColumns;

      for (int i = 0; i < lines.length - 1; i++) {
        components.add(new RadCaptionTableColumn(container,
                                                 lines[i],
                                                 lines[i + 1] - lines[i],
                                                 emptyColumns[i]));
      }
    }
    else {
      for (RadComponent component : children) {
        components.add(new RadCaptionTableRow((RadViewComponent)component));
      }
    }

    return components;
  }

  private RadLayout myCaptionColumnLayout = RadViewLayout.INSTANCE;
  private RadLayout myCaptionRowLayout;

  @Override
  @NotNull
  public RadLayout getCaptionLayout(final EditableArea mainArea, boolean horizontal) {
    if (horizontal) {
      // TODO
      return myCaptionColumnLayout;
    }

    if (myCaptionRowLayout == null) {
      myCaptionRowLayout = new RadViewLayout() {
        @Override
        public EditOperation processChildOperation(OperationContext context) {
          if (context.isMove()) {
            return new CaptionFlowBaseOperation(myContainer, context, false, mainArea) {
              @Override
              public void setComponents(List<RadComponent> components) {
                super.setComponents(components);
                myMainContext.setComponents(getRowComponents(components));
              }

              @Override
              public void showFeedback() {
                myMainContext.setLocation(SwingUtilities.convertPoint(myContext.getArea().getFeedbackLayer(), myContext.getLocation(),
                                                                      myMainContext.getArea().getFeedbackLayer()));
                super.showFeedback();
              }

              @Override
              protected void execute(@Nullable RadComponent insertBefore) throws Exception {
                super.execute(insertBefore);
                AbstractEditOperation.execute(myMainContext,
                                              (RadViewComponent)RadTableLayout.this.myContainer,
                                              getRowComponents(myComponents),
                                              getRowComponent(insertBefore));
              }
            };
          }
          return null;
        }
      };
    }
    return myCaptionRowLayout;
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

  private abstract class CaptionFlowBaseOperation extends FlowBaseOperation {
    protected OperationContext myMainContext;
    protected FlowBaseOperation myMainOperation;

    public CaptionFlowBaseOperation(RadComponent container, OperationContext context, boolean horizontal, EditableArea mainArea) {
      super(container, context, horizontal);
      myMainContext = new OperationContext(context.getType());
      myMainContext.setArea(mainArea);
      myMainOperation = new MainFlowBaseOperation(RadTableLayout.this.myContainer, myMainContext, horizontal);
    }

    @Override
    public void showFeedback() {
      super.showFeedback();
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
    }
  }

  private static class MainFlowBaseOperation extends FlowBaseOperation {
    public MainFlowBaseOperation(RadComponent container, OperationContext context, boolean horizontal) {
      super(container, context, horizontal);
    }

    @Override
    protected void execute(@Nullable RadComponent insertBefore) throws Exception {
    }
  }
}