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

import com.intellij.android.designer.model.layout.RadLinearLayout;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadTableRowLayout extends RadLinearLayout {
  private static final String[] LAYOUT_PARAMS = {"TableRow_Cell", "LinearLayout_Layout", "ViewGroup_MarginLayout"};

  @Override
  @NotNull
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
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
    return isTableParent() ? NON_RESIZE_DECORATOR : super.getChildSelectionDecorator(component, selection);
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
    if (!isTableParent()) {
      super.addSelectionActions(designer, actionGroup, shortcuts, selection);
    }
  }
}