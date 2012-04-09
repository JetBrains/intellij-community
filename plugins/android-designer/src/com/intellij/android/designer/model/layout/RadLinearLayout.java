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
import com.intellij.android.designer.designSurface.layout.LayoutMarginOperation;
import com.intellij.android.designer.designSurface.layout.LinearLayoutOperation;
import com.intellij.android.designer.designSurface.layout.ResizeOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableRunnable;
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

  private static final Icon myHorizontalIcon = IconLoader.getIcon("/com/intellij/android/designer/icons/LinearLayout.png");
  private static final Icon myVerticalIcon = IconLoader.getIcon("/com/intellij/android/designer/icons/LinearLayout2.png");
  private static final Icon myHorizontalOverrideIcon = IconLoader.getIcon("/com/intellij/android/designer/icons/LinearLayout3.png");

  private ResizeSelectionDecorator mySelectionDecorator;
  private FlowStaticDecorator myLineDecorator;

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  private boolean isHorizontal() {
    return !"vertical".equals(((RadViewComponent)myContainer).getTag().getAttributeValue("android:orientation"));
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
    else if (context.is(LayoutMarginOperation.TYPE)) {
      return new LayoutMarginOperation(context);
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

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new ResizeSelectionDecorator(Color.red, 1) {
        @Override
        protected boolean visible(RadComponent component, ResizePoint point) {
          if (point.getType() == LayoutMarginOperation.TYPE) {
            boolean horizontal = isHorizontal();
            Pair<Gravity, Gravity> gravity = Gravity.getSides(component);
            int direction = ((DirectionResizePoint)point).getDirection();

            if (direction == Position.WEST) { // left
              return horizontal || gravity.first != Gravity.right;
            }
            if (direction == Position.EAST) { // right
              return horizontal || gravity.first != Gravity.left;
            }
            if (direction == Position.NORTH) { // top
              return !horizontal || gravity.second != Gravity.bottom;
            }
            if (direction == Position.SOUTH) { // bottom
              return !horizontal || gravity.second != Gravity.top;
            }
          }
          return true;
        }
      };
    }

    mySelectionDecorator.clear();
    if (selection.size() == 1) {
      LayoutMarginOperation.points(mySelectionDecorator);
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
  public void addSelectionActions(final DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  final List<RadComponent> selection) {
    for (RadComponent component : selection) {
      if (component.getParent() != myContainer) {
        return;
      }
    }

    createOrientationAction(designer, actionGroup, shortcuts, Arrays.asList(myContainer));

    AbstractComboBoxAction<Gravity> action = new AbstractComboBoxAction<Gravity>() {
      private Gravity mySelection;

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        boolean horizontal = isHorizontal();
        Gravity unknown = horizontal ? Gravity.left : Gravity.top;
        setItems(horizontal ? VERTICALS : HORIZONTALS, unknown);

        Iterator<RadComponent> I = selection.iterator();
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
        designer.getToolProvider().execute(new ThrowableRunnable<Exception>() {
          @Override
          public void run() throws Exception {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                LinearLayoutOperation.execute(isHorizontal(), item, selection);
              }
            });
          }
        }, "Change attribute 'gravity'", true);

        return false;
      }

      @Override
      public void update() {
      }
    };
    Presentation presentation = action.getTemplatePresentation();
    presentation.setDescription("Gravity");
    presentation.setIcon(Gravity.ICON);

    actionGroup.add(action);
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

  private static class OrientationAction extends AnAction {
    private final DesignerEditorPanel myDesigner;
    private final List<RadComponent> myComponents;
    private boolean mySelection;

    public OrientationAction(DesignerEditorPanel designer, List<RadComponent> components, boolean horizontal, boolean override) {
      myDesigner = designer;
      myComponents = components;
      mySelection = horizontal;
      update(getTemplatePresentation(), override);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySelection = !mySelection;
      update(e.getPresentation(), false);
      myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
        @Override
        public void run() throws Exception {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              String value = mySelection ? "horizontal" : "vertical";
              for (RadComponent component : myComponents) {
                ((RadViewComponent)component).getTag().setAttribute("android:orientation", value);
              }
            }
          });
        }
      }, "Change attribute 'orientation'", true);
    }

    public void update(Presentation presentation, boolean override) {
      String text;
      Icon icon;

      if (override) {
        text = "Override orientation to horizontal";
        icon = myHorizontalOverrideIcon;
      }
      else {
        text = "Convert orientation to " + (mySelection ? "vertical" : "horizontal");
        icon = mySelection ? myHorizontalIcon : myVerticalIcon;
      }

      presentation.setText(text);
      presentation.setDescription(text);
      presentation.setIcon(icon);
    }
  }
}