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
package com.intellij.android.designer.model.layout.table;

import com.intellij.android.designer.designSurface.layout.actions.LayoutSpanOperation;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.AbstractGravityAction;
import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.android.designer.model.layout.RadLinearLayout;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadTableRowLayout extends RadLinearLayout {
  private static final String[] LAYOUT_PARAMS = {"TableRow_Cell", "LinearLayout_Layout", "ViewGroup_MarginLayout"};

  private ResizeSelectionDecorator mySelectionDecorator;

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  public static boolean is(RadComponent component) {
    return component.getLayout() instanceof RadTableRowLayout;
  }

  private boolean isTableParent() {
    return myContainer.getParent() instanceof RadTableLayoutComponent;
  }

  @Override
  protected boolean isHorizontal() {
    return true;
  }

  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (!isTableParent() || context.isTree()) {
      return super.processChildOperation(context);
    }
    if (context.is(LayoutSpanOperation.TYPE)) {
      return new LayoutSpanOperation(context);
    }
    return null;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    if (!isTableParent()) {
      super.addStaticDecorators(decorators, selection);
    }
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (isTableParent()) {
      if (mySelectionDecorator == null) {
        mySelectionDecorator = new ResizeSelectionDecorator(Color.red, 1);
      }

      mySelectionDecorator.clear();
      if (selection.size() == 1) {
        LayoutSpanOperation.tablePoints(mySelectionDecorator);
      }
      return mySelectionDecorator;
    }
    return super.getChildSelectionDecorator(component, selection);
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
    if (!isTableParent()) {
      super.addContainerSelectionActions(designer, actionGroup, shortcuts, selection);
    }
  }

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    if (isTableParent()) {
      if (selection.get(selection.size() - 1).getParent() != myContainer) {
        return;
      }
      for (RadComponent component : selection) {
        if (!is(component.getParent())) {
          return;
        }
      }

      AbstractGravityAction<Gravity> action = new AbstractGravityAction<Gravity>(designer, selection) {
        private List<Gravity> mySelection;

        @NotNull
        @Override
        protected DefaultActionGroup createPopupActionGroup(JComponent button) {
          Iterator<RadComponent> I = myComponents.iterator();
          int flags = Gravity.getFlags(I.next());
          while (I.hasNext()) {
            if (flags != Gravity.getFlags(I.next())) {
              flags = 0;
              break;
            }
          }
          mySelection = Gravity.flagToValues(flags);

          return super.createPopupActionGroup(button);
        }

        @Override
        protected void update(Gravity item, Presentation presentation, boolean popup) {
          if (popup) {
            presentation.setIcon(mySelection.contains(item) ? CHECKED : null);
            presentation.setText(item.name());
          }
        }

        @Override
        protected boolean selectionChanged(Gravity item) {
          int index = mySelection.indexOf(item);
          if (index == -1) {
            mySelection.add(item);
          }
          else {
            mySelection.remove(index);
          }
          execute(new Runnable() {
            @Override
            public void run() {
              if (mySelection.isEmpty()) {
                for (RadComponent component : myComponents) {
                  ModelParser.deleteAttribute(component, "android:layout_gravity");
                }
              }
              else {
                String value = StringUtil.join(mySelection, new Function<Gravity, String>() {
                  @Override
                  public String fun(Gravity gravity) {
                    return gravity.name();
                  }
                }, "|");

                for (RadComponent component : myComponents) {
                  XmlTag tag = ((RadViewComponent)component).getTag();
                  tag.setAttribute("android:layout_gravity", value);
                }
              }
            }
          });

          return false;
        }

        @Override
        public void setSelection(Gravity selection) {
        }
      };
      action.setItems(Arrays.asList(Gravity.values()), null);
      actionGroup.add(action);
    }
    else {
      super.addSelectionActions(designer, actionGroup, shortcuts, selection);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Caption
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public ICaption getCaption(RadComponent component) {
    return isTableParent() ? myContainer.getParent().getLayout().getCaption(component) : null;
  }
}