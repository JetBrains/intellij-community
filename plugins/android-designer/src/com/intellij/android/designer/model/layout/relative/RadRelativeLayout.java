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
package com.intellij.android.designer.model.layout.relative;

import com.intellij.android.designer.designSurface.TreeDropToOperation;
import com.intellij.android.designer.designSurface.layout.RelativeLayoutOperation;
import com.intellij.android.designer.designSurface.layout.actions.RelativeLayoutResizeOperation;
import com.intellij.android.designer.designSurface.layout.relative.RelativeDecorator;
import com.intellij.android.designer.model.PropertyParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.android.designer.model.layout.actions.AllGravityAction;
import com.intellij.android.designer.propertyTable.AttributeProperty;
import com.intellij.android.designer.propertyTable.JavadocParser;
import com.intellij.android.designer.propertyTable.RelativeIdAttributeProperty;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadRelativeLayout extends RadViewLayoutWithData implements ILayoutDecorator {
  private static final String[] LAYOUT_PARAMS = {"RelativeLayout_Layout", "ViewGroup_MarginLayout"};

  private RelativeDecorator myRelativeDecorator;
  private ResizeSelectionDecorator mySelectionDecorator;

  @NotNull
  @Override
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  @Override
  public void configureProperties(List<Property> properties) {
    CompoundProperty alignComponent =
      new CompoundProperty("layout:alignComponent",
                           JavadocParser
                             .build("layout:alignComponent", "Positions the edges of this view to the edges of the another view."));
    PropertyParser.moveProperties(properties, alignComponent,
                                  "layout:alignTop", "top:top",
                                  "layout:below", "top:bottom",
                                  "layout:alignLeft", "left:left",
                                  "layout:toRightOf", "left:right",
                                  "layout:alignBottom", "bottom:bottom",
                                  "layout:above", "bottom:top",
                                  "layout:alignRight", "right:right",
                                  "layout:toLeftOf", "right:left",
                                  "layout:alignBaseline", "baseline:baseline");
    List<Property<RadViewComponent>> alignChildren = alignComponent.getChildren(null);
    int alignSize = alignChildren.size();
    for (int i = 0; i < alignSize; i++) {
      alignChildren.set(i, new RelativeIdAttributeProperty((AttributeProperty)alignChildren.get(i)));
    }
    properties.add(alignComponent);

    CompoundProperty alignParent =
      new CompoundProperty("layout:alignParent",
                           JavadocParser.build("layout:alignParent", "Positions the edges of this view to the edges of the parent."));
    PropertyParser.moveProperties(properties, alignParent,
                                  "layout:alignParentTop", "top",
                                  "layout:alignParentLeft", "left",
                                  "layout:alignParentBottom", "bottom",
                                  "layout:alignParentRight", "right",
                                  "layout:alignWithParentIfMissing", "missing");
    properties.add(alignParent);

    PropertyTable.extractProperty(properties, "layout:centerInParent");
    PropertyTable.extractProperty(properties, "layout:centerHorizontal");
    PropertyTable.extractProperty(properties, "layout:centerVertical");
    properties.add(new CenterProperty());
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
      return new RelativeLayoutOperation(myContainer, context);
    }
    if (context.is(RelativeLayoutResizeOperation.TYPE)) {
      return new RelativeLayoutResizeOperation(context);
    }
    return null;
  }

  private RelativeDecorator getRelativeDecorator() {
    if (myRelativeDecorator == null) {
      myRelativeDecorator = new RelativeDecorator(myContainer);
    }
    return myRelativeDecorator;
  }

  @Override
  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
    for (RadComponent component : selection) {
      if (component.getParent() == myContainer) {
        if (!(myContainer.getParent().getLayout() instanceof ILayoutDecorator)) {
          decorators.add(getRelativeDecorator());
        }
        return;
      }
    }
    super.addStaticDecorators(decorators, selection);
  }

  @Override
  public ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection) {
    if (mySelectionDecorator == null) {
      mySelectionDecorator = new ResizeSelectionDecorator(JBColor.RED, 1);
      RelativeLayoutResizeOperation.points(mySelectionDecorator);
    }
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
      if (!(component.getLayout() instanceof RadRelativeLayout)) {
        return;
      }
    }

    actionGroup.add(new AllGravityAction(designer, Arrays.asList(myContainer)));
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
      if (!(component.getParent() instanceof RadRelativeLayoutComponent)) {
        return;
      }
    }

    actionGroup.add(new AllGravityAction(designer, Arrays.asList(myContainer)));
  }
}