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
package com.intellij.android.designer.model.morphing;

import com.android.SdkConstants;
import com.intellij.android.designer.model.*;
import com.intellij.android.designer.model.layout.RadLinearLayout;
import com.intellij.android.designer.model.layout.RadRadioGroupLayout;
import com.intellij.android.designer.model.layout.grid.RadGridLayout;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.android.designer.model.layout.table.RadTableLayout;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class LinearLayout {
  public static RadViewComponent RadioGroup(RadViewComponent component, MetaModel target) throws Exception {
    final boolean horizontal = ((RadLinearLayout)component.getLayout()).isHorizontal();

    return new ComponentMorphingTool(component, component, target, new RadRadioGroupLayout()) {
      @Override
      protected void convertTag() {
        if (horizontal) {
          myNewComponent.getTag().setAttribute("orientation", SdkConstants.NS_RESOURCES, "horizontal");
        }
        else {
          ModelParser.deleteAttribute(myNewComponent, "orientation");
        }
      }
    }.result();
  }

  public static RadViewComponent GridLayout(RadViewComponent component, MetaModel target) throws Exception {
    return new ComponentMorphingTool(component, new RadGridLayoutComponent(), target, new RadGridLayout()).result();
  }

  public static RadViewComponent TableLayout(RadViewComponent component, MetaModel target) throws Exception {
    boolean horizontal = ((RadLinearLayout)component.getLayout()).isHorizontal();
    return TableLayout(component, target, horizontal);
  }

  public static RadViewComponent TableLayout(RadViewComponent component, MetaModel target, final boolean horizontal) throws Exception {
    final MetaModel tableRowModel = ViewsMetaManager.getInstance(component.getTag().getProject()).getModelByTag("TableRow");

    return new ComponentMorphingTool(component, new RadTableLayoutComponent(), target, new RadTableLayout()) {
      @Override
      protected void convertChildren() throws Exception {
        List<RadComponent> oldChildren = new ArrayList<RadComponent>(myOldComponent.getChildren());

        if (horizontal) {
          RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
          ModelParser.addComponent(myNewComponent, newRowComponent, null);

          for (RadComponent childComponent : oldChildren) {
            ModelParser.moveComponent(newRowComponent, (RadViewComponent)childComponent, null);
          }
        }
        else {
          for (RadComponent childComponent : oldChildren) {
            RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
            ModelParser.addComponent(myNewComponent, newRowComponent, null);
            ModelParser.moveComponent(newRowComponent, (RadViewComponent)childComponent, null);
          }
        }
      }

      @Override
      protected void convertTag() {
        ModelParser.deleteAttribute(myNewComponent, "orientation");
      }

      @Override
      protected void loadChildProperties(PropertyParser propertyParser) throws Exception {
      }
    }.result();
  }
}