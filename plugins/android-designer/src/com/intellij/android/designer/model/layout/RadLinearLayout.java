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

import com.android.SdkConstants;
import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.layout.LinearLayoutOperation;
import com.intellij.android.designer.designSurface.layout.actions.LayoutMarginOperation;
import com.intellij.android.designer.designSurface.layout.actions.LayoutWeightOperation;
import com.intellij.android.designer.designSurface.layout.actions.ResizeOperation;
import com.intellij.android.designer.designSurface.layout.flow.FlowStaticDecorator;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.android.designer.model.layout.actions.AbstractGravityAction;
import com.intellij.android.designer.model.layout.actions.OrientationAction;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadLinearLayout extends RadViewLayoutWithData implements ILayoutDecorator {
  private static final String[] LAYOUT_PARAMS = {"LinearLayout_Layout", "ViewGroup_MarginLayout"};

  private ResizeSelectionDecorator mySelectionDecorator;
  private FlowStaticDecorator myLineDecorator;

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  public boolean isHorizontal() {
    return !"vertical".equals(((RadViewComponent)myContainer).getTag().getAttributeValue("orientation", SdkConstants.NS_RESOURCES));
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
      return new LinearLayoutOperation(myContainer, context, isHorizontal());
    }
    if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
    if (context.is(LayoutMarginOperation.TYPE)) {
      return new LayoutMarginOperation(context);
    }
    if (context.is(LayoutWeightOperation.TYPE)) {
      return new LayoutWeightOperation(context);
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

  private static final int POINTS_SIZE = 16;

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new ResizeSelectionDecorator(JBColor.RED, 1) {
        @Override
        protected boolean visible(RadComponent component, ResizePoint point) {
          if (point.getType() == LayoutMarginOperation.TYPE) {
            boolean horizontal = isHorizontal();
            Pair<Gravity, Gravity> gravity = Gravity.getSides(component);
            int direction = ((DirectionResizePoint)point).getDirection();
            Rectangle bounds = component.getBounds();
            boolean goodWidth = bounds.width >= POINTS_SIZE;
            boolean goodHeight = bounds.height >= POINTS_SIZE;

            if (direction == Position.WEST) { // left
              return (horizontal || gravity.first != Gravity.right) && goodHeight;
            }
            if (direction == Position.EAST) { // right
              return (horizontal || gravity.first != Gravity.left) && goodHeight;
            }
            if (direction == Position.NORTH) { // top
              return (!horizontal || gravity.second != Gravity.bottom) && goodWidth;
            }
            if (direction == Position.SOUTH) { // bottom
              return (!horizontal || gravity.second != Gravity.top) && goodWidth;
            }
          }
          if (point.getType() == LayoutWeightOperation.TYPE) {
            int direction = ((DirectionResizePoint)point).getDirection();

            if (direction == Position.EAST) { // right
              return isHorizontal() && component.getBounds().height >= POINTS_SIZE;
            }
            if (direction == Position.SOUTH) { // bottom
              return !isHorizontal() && component.getBounds().width >= POINTS_SIZE;
            }
          }
          return true;
        }
      };
    }

    mySelectionDecorator.clear();
    if (selection.size() == 1) {
      LayoutMarginOperation.points(mySelectionDecorator);
      LayoutWeightOperation.point(mySelectionDecorator);
    }
    ResizeOperation.points(mySelectionDecorator);

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
      if (!(component.getLayout() instanceof RadLinearLayout)) {
        return;
      }
    }

    createOrientationAction(designer, actionGroup, shortcuts, selection);
  }

  private static final List<Gravity> HORIZONTALS = Arrays.asList(Gravity.left, Gravity.center, Gravity.right, null);
  private static final List<Gravity> VERTICALS = Arrays.asList(Gravity.top, Gravity.center, Gravity.bottom, null);

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    for (RadComponent component : selection) {
      if (component.getParent() != myContainer) {
        return;
      }
    }

    createOrientationAction(designer, actionGroup, shortcuts, Arrays.asList(myContainer));
    actionGroup.add(new GravityAction(designer, selection));
  }

  private class GravityAction extends AbstractGravityAction<Gravity> {
    private Gravity mySelection;

    public GravityAction(DesignerEditorPanel designer, List<RadComponent> components) {
      super(designer, components);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      boolean horizontal = isHorizontal();
      Gravity unknown = horizontal ? Gravity.left : Gravity.top;
      setItems(horizontal ? VERTICALS : HORIZONTALS, unknown);

      Iterator<RadComponent> I = myComponents.iterator();
      mySelection = LinearLayoutOperation.getGravity(horizontal, I.next());

      while (I.hasNext()) {
        if (mySelection != LinearLayoutOperation.getGravity(horizontal, I.next())) {
          mySelection = unknown;
          break;
        }
      }

      return super.createPopupActionGroup(button);
    }

    @Override
    protected void update(Gravity item, Presentation presentation, boolean popup) {
      if (popup) {
        presentation.setIcon(mySelection == item ? CHECKED : null);
        presentation.setText(item == null ? "fill" : item.name());
      }
    }

    @Override
    protected boolean selectionChanged(final Gravity item) {
      execute(new Runnable() {
        @Override
        public void run() {
          LinearLayoutOperation.execute(isHorizontal(), item, myComponents);
        }
      });

      return false;
    }

    @Override
    public void update() {
    }
  }

  private static void createOrientationAction(DesignerEditorPanel designer,
                                              DefaultActionGroup actionGroup,
                                              JComponent shortcuts,
                                              List<RadComponent> components) {
    boolean override = false;
    Iterator<RadComponent> I = components.iterator();
    boolean horizontal = ((RadLinearLayout)I.next().getLayout()).isHorizontal();

    while (I.hasNext()) {
      boolean next = ((RadLinearLayout)I.next().getLayout()).isHorizontal();
      if (horizontal != next) {
        override = true;
        break;
      }
    }

    actionGroup.add(new OrientationAction(designer, components, horizontal, override));
  }

  @Override
  public boolean isWrapIn(List<RadComponent> components) {
    List<RadComponent> children = myContainer.getChildren();

    int[] indexes = new int[components.size()];
    for (int i = 0; i < indexes.length; i++) {
      indexes[i] = children.indexOf(components.get(i));
    }
    Arrays.sort(indexes);

    for (int i = 0; i < indexes.length - 1; i++) {
      if (indexes[i + 1] - indexes[i] != 1) {
        return false;
      }
    }

    return true;
  }

  @Override
  public void wrapIn(final RadViewComponent newParent, final List<RadViewComponent> components) throws Exception {
    final boolean horizontal = isHorizontal();
    RadViewComponent firstComponent = components.get(0);
    boolean single = components.size() == 1;
    String layoutWidth = single ? firstComponent.getTag().getAttributeValue("layout_width", SdkConstants.NS_RESOURCES) : "wrap_content";
    String layoutHeight = single ? firstComponent.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES) : "wrap_content";
    String layoutGravity = firstComponent.getTag().getAttributeValue("layout_gravity", SdkConstants.NS_RESOURCES);

    if (horizontal) {
      for (RadViewComponent component : components.subList(1, components.size())) {
        String height = component.getTag().getAttributeValue("layout_height", SdkConstants.NS_RESOURCES);
        if ("fill_parent".equals(height) || "match_parent".equals(height)) {
          layoutHeight = "fill_parent";
          layoutGravity = null;
        }
        if (layoutGravity != null &&
            layoutGravity.equals(component.getTag().getAttributeValue("layout_gravity", SdkConstants.NS_RESOURCES))) {

        }
      }
    }
    else {

    }

    if (newParent.getLayout() instanceof RadLinearLayout) {
      RadLinearLayout layout = (RadLinearLayout)newParent.getLayout();
      if (horizontal != layout.isHorizontal()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            newParent.getTag().setAttribute("orientation", SdkConstants.NS_RESOURCES, horizontal ? "horizontal" : "vertical");
          }
        });
      }
    }
    else {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          for (RadViewComponent component : components) {
            ModelParser.deleteAttribute(component.getTag(), "layout_gravity");
          }
        }
      });
    }
  }
}