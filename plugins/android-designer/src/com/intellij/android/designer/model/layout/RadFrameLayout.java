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
import com.intellij.android.designer.designSurface.layout.FrameLayoutOperation;
import com.intellij.android.designer.designSurface.layout.LayoutMarginOperation;
import com.intellij.android.designer.designSurface.layout.ResizeOperation;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.designer.designSurface.ComponentDecorator;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.DirectionResizePoint;
import com.intellij.designer.designSurface.selection.ResizePoint;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.utils.Position;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadFrameLayout extends RadViewLayoutWithData {
  private static final String[] LAYOUT_PARAMS = {"FrameLayout_Layout", "ViewGroup_MarginLayout"};

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
        return new TreeDropToOperation(myContainer, context);
      }
      return new FrameLayoutOperation((RadViewComponent)myContainer, context);
    }
    else if (context.is(ResizeOperation.TYPE)) {
      return new ResizeOperation(context);
    }
    else if (context.is(LayoutMarginOperation.TYPE)) {
      return new LayoutMarginOperation(context);
    }
    return null;
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new ResizeSelectionDecorator(Color.red, 1) {
        @Override
        protected boolean visible(RadComponent component, ResizePoint point) {
          if (point.getType() == LayoutMarginOperation.TYPE) {
            Pair<Gravity, Gravity> gravity = Gravity.getSides(component);
            int direction = ((DirectionResizePoint)point).getDirection();

            if (direction == Position.WEST) { // left
              return gravity.first == Gravity.left || gravity.first == Gravity.center;
            }
            if (direction == Position.EAST) { // right
              return gravity.first == Gravity.right || gravity.first == Gravity.center;
            }
            if (direction == Position.NORTH) { // top
              return gravity.second == Gravity.top || gravity.second == Gravity.center;
            }
            if (direction == Position.SOUTH) { // bottom
              return gravity.second == Gravity.bottom || gravity.second == Gravity.center;
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

  private static List<Pair<Boolean, Gravity>> ITEMS = Arrays
    .asList(Pair.create(Boolean.FALSE, Gravity.top), Pair.create(Boolean.FALSE, Gravity.center),
            Pair.create(Boolean.FALSE, Gravity.bottom), null, Pair.create(Boolean.TRUE, Gravity.left),
            Pair.create(Boolean.TRUE, Gravity.center), Pair.create(Boolean.TRUE, Gravity.right));

  @Override
  public void addSelectionActions(final DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  final List<RadComponent> selection) {
    if (selection.size() != 1) {
      return;
    }

    AbstractComboBoxAction<Pair<Boolean, Gravity>> action = new AbstractComboBoxAction<Pair<Boolean, Gravity>>() {
      private Pair<Gravity, Gravity> myGravity;

      @Override
      protected boolean addSeparator(DefaultActionGroup actionGroup, Pair<Boolean, Gravity> item) {
        if (item == null) {
          actionGroup.addSeparator();
          return true;
        }
        return false;
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        myGravity = Gravity.getSides(selection.get(0));
        return super.createPopupActionGroup(button);
      }

      @Override
      protected void update(Pair<Boolean, Gravity> item, Presentation presentation, boolean popup) {
        if (popup) {
          Gravity selection = item.first ? myGravity.first : myGravity.second;
          presentation.setIcon(selection == item.second ? CHECKED : null);
          presentation.setText(item.second.name());
        }
      }

      @Override
      protected boolean selectionChanged(Pair<Boolean, Gravity> item) {
        Gravity oldSelection = item.first ? myGravity.first : myGravity.second;
        if (oldSelection != item.second) {
          final String gravity =
            item.first ? Gravity.getValue(item.second, myGravity.second) : Gravity.getValue(myGravity.first, item.second);
          designer.getToolProvider().execute(new ThrowableRunnable<Exception>() {
            @Override
            public void run() throws Exception {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  ((RadViewComponent)selection.get(0)).getTag().setAttribute("android:layout_gravity", gravity);
                }
              });
            }
          }, "Change attribute 'gravity'", true);
        }

        return false;
      }

      @Override
      public void setSelection(Pair<Boolean, Gravity> selection) {
      }
    };
    Presentation presentation = action.getTemplatePresentation();
    presentation.setDescription("Gravity");
    presentation.setIcon(Gravity.ICON);
    action.setItems(ITEMS, null);

    actionGroup.add(action);
  }
}