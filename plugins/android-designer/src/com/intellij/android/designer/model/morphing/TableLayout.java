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
import com.intellij.android.designer.model.ComponentMorphingTool;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.grid.RadGridLayout;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.android.designer.model.layout.table.RadTableLayoutComponent;
import com.intellij.android.designer.model.layout.table.RadTableRowLayout;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;

/**
 * @author Alexander Lobas
 */
public class TableLayout {
  private static final String TABLE_ROW_KEY = "TableRow";

  public static RadViewComponent GridLayout(RadViewComponent component, MetaModel target) throws Exception {
    final RadComponent[][] components = ((RadTableLayoutComponent)component).getGridComponents(false);

    return new ComponentMorphingTool(component, new RadGridLayoutComponent(), target, new RadGridLayout()) {
      @Override
      protected void convertChildren() throws Exception {
        for (final RadComponent rowComponent : new ArrayList<RadComponent>(myOldComponent.getChildren())) {
          if (RadTableRowLayout.is(rowComponent)) {
            for (RadComponent cellComponent : new ArrayList<RadComponent>(rowComponent.getChildren())) {
              ModelParser.moveComponent(myOldComponent, (RadViewComponent)cellComponent, (RadViewComponent)rowComponent);
            }
            rowComponent.delete();
          }
          else {
            rowComponent.setClientProperty(TABLE_ROW_KEY, Boolean.TRUE);
          }
        }
        super.convertChildren();
      }

      @Override
      protected void convertTag() {
        XmlTag tag = myNewComponent.getTag();
        if (components.length > 0) {
          String columnCount = Integer.toString(components[0].length);
          tag.setAttribute("rowCount", SdkConstants.NS_RESOURCES, Integer.toString(components.length));
          tag.setAttribute("columnCount", SdkConstants.NS_RESOURCES, columnCount);

          for (int i = 0; i < components.length; i++) {
            RadComponent[] rowComponents = components[i];

            RadComponent firstCellComponent = rowComponents[0];
            if (firstCellComponent != null && firstCellComponent.extractClientProperty(TABLE_ROW_KEY) == Boolean.TRUE) {
              XmlTag cellTag = ((RadViewComponent)firstCellComponent).getTag();
              ModelParser.deleteAttribute(cellTag, "layout_span");
              cellTag.setAttribute("layout_column", SdkConstants.NS_RESOURCES, "0");
              cellTag.setAttribute("layout_columnSpan", SdkConstants.NS_RESOURCES, columnCount);
              cellTag.setAttribute("layout_gravity", SdkConstants.NS_RESOURCES, "fill_horizontal");
            }

            for (RadComponent cellComponent : rowComponents) {
              if (cellComponent != null) {
                XmlTag cellTag = ((RadViewComponent)cellComponent).getTag();
                cellTag.setAttribute("layout_row", SdkConstants.NS_RESOURCES, Integer.toString(i));
                break;
              }
            }
          }

          for (RadComponent childComponent : myNewComponent.getChildren()) {
            XmlAttribute attribute = ((RadViewComponent)childComponent).getTag().getAttribute("layout_span", SdkConstants.NS_RESOURCES);
            if (attribute != null) {
              attribute.setName(attribute.getNamespacePrefix() + ":layout_columnSpan");
            }
          }
        }
      }
    }.result();
  }
}