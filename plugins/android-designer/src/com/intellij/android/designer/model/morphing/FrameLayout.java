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
import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.android.designer.model.layout.grid.RadGridLayout;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.android.designer.model.layout.table.RadTableLayout;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class FrameLayout {
  private static final String COLUMN_KEY = "Column";

  public static RadViewComponent TableLayout(RadViewComponent component, MetaModel target) throws Exception {
    final MetaModel tableRowModel = ViewsMetaManager.getInstance(component.getTag().getProject()).getModelByTag("TableRow");

    return new ComponentMorphingTool(component, new RadTableLayoutComponent(), target, new RadTableLayout()) {
      @Override
      protected void convertChildren() throws Exception {
        RadViewComponent[] rowComponents = new RadViewComponent[3];
        Map<RadViewComponent, Map<Gravity, List<RadViewComponent>>> components =
          new HashMap<RadViewComponent, Map<Gravity, List<RadViewComponent>>>();

        for (int i = 0; i < rowComponents.length; i++) {
          RadViewComponent newRowComponent = ModelParser.createComponent(null, tableRowModel);
          ModelParser.addComponent(myNewComponent, newRowComponent, null);
          rowComponents[i] = newRowComponent;
        }

        for (RadComponent childComponent : new ArrayList<RadComponent>(myOldComponent.getChildren())) {
          Pair<Gravity, Gravity> sides = Gravity.getSides(childComponent);

          RadViewComponent rowComponent = rowComponents[getRowIndex(sides.second)];
          Map<Gravity, List<RadViewComponent>> rowMap = components.get(rowComponent);
          if (rowMap == null) {
            rowMap = new HashMap<Gravity, List<RadViewComponent>>();
            components.put(rowComponent, rowMap);
          }

          List<RadViewComponent> rowChildren = rowMap.get(sides.first);
          if (rowChildren == null) {
            rowChildren = new ArrayList<RadViewComponent>();
            rowMap.put(sides.first, rowChildren);
          }
          rowChildren.add((RadViewComponent)childComponent);
        }

        for (RadViewComponent rowComponent : rowComponents) {
          Map<Gravity, List<RadViewComponent>> rowMap = components.get(rowComponent);
          boolean column = moveComponents(rowComponent, rowMap.remove(Gravity.left), -1);
          column = moveComponents(rowComponent, rowMap.remove(Gravity.center), column ? 1 : -1);
          moveComponents(rowComponent, rowMap.remove(Gravity.right), column ? 2 : -1);

          for (List<RadViewComponent> rowChildren : rowMap.values()) {
            moveComponents(rowComponent, rowChildren, -1);
          }
        }
      }

      @Override
      protected void convertTag() {
        for (RadComponent rowComponent : myNewComponent.getChildren()) {
          for (RadComponent cellComponent : rowComponent.getChildren()) {
            XmlTag childTag = ((RadViewComponent)cellComponent).getTag();
            ModelParser.deleteAttribute(childTag, "layout_gravity");

            Integer column = cellComponent.extractClientProperty(COLUMN_KEY);
            if (column != null) {
              childTag.setAttribute("layout_column", SdkConstants.NS_RESOURCES, column.toString());
            }
          }
        }
      }

      @Override
      protected void loadChildProperties(PropertyParser propertyParser) throws Exception {
      }
    }.result();
  }

  private static boolean moveComponents(RadViewComponent container, Collection<RadViewComponent> children, int column) throws Exception {
    if (children != null) {
      for (RadViewComponent childComponent : children) {
        if (column != -1) {
          childComponent.setClientProperty(COLUMN_KEY, column);
        }
        ModelParser.moveComponent(container, childComponent, null);
      }
      return false;
    }
    return true;
  }

  private static int getRowIndex(Gravity side) {
    if (side == Gravity.center) {
      return 1;
    }
    if (side == Gravity.bottom) {
      return 2;
    }
    return 0;
  }

  public static RadViewComponent GridLayout(RadViewComponent component, MetaModel target) throws Exception {
    return new ComponentMorphingTool(component, new RadGridLayoutComponent(), target, new RadGridLayout()) {
      @Override
      protected void convertTag() {
        XmlTag tag = myNewComponent.getTag();
        tag.setAttribute("rowCount", SdkConstants.NS_RESOURCES, "3");
        tag.setAttribute("columnCount", SdkConstants.NS_RESOURCES, "3");

        for (RadComponent childComponent : myNewComponent.getChildren()) {
          XmlTag childTag = ((RadViewComponent)childComponent).getTag();
          Pair<Gravity, Gravity> sides = Gravity.getSides(childComponent);
          ModelParser.deleteAttribute(childTag, "layout_gravity");
          childTag.setAttribute("layout_row", SdkConstants.NS_RESOURCES, getRowIndexValue(sides.second));
          childTag.setAttribute("layout_column", SdkConstants.NS_RESOURCES, getColumnIndexValue(sides.first));
        }
      }
    }.result();
  }

  private static String getRowIndexValue(Gravity side) {
    if (side == Gravity.center) {
      return "1";
    }
    if (side == Gravity.bottom) {
      return "2";
    }
    return "0";
  }

  private static String getColumnIndexValue(Gravity side) {
    if (side == Gravity.center) {
      return "1";
    }
    if (side == Gravity.right) {
      return "2";
    }
    return "0";
  }
}