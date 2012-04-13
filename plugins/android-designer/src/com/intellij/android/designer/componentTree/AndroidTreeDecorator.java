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
package com.intellij.android.designer.componentTree;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.palette.Item;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public final class AndroidTreeDecorator extends TreeComponentDecorator {
  @Override
  public void decorate(RadComponent component, ColoredTreeCellRenderer renderer) {
    MetaModel metaModel = component.getMetaModel();

    String id = getPropertyValue(component, "id");
    if (!StringUtil.isEmpty(id)) {
      id = id.substring(id.indexOf('/') + 1);
      if (!StringUtil.isEmpty(id)) {
        renderer.append(id + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }

    StringBuilder fullTitle = new StringBuilder();
    Item item = metaModel.getPaletteItem();
    if (item != null) {
      fullTitle.append(item.getTitle());
    }

    String title = metaModel.getTitle();
    if (title != null) {
      int start = title.indexOf('%');
      if (start != -1) {
        int end = title.indexOf('%', start + 1);
        if (end != -1) {
          String variable = title.substring(start + 1, end);
          String value;
          if ("tag".equals(variable)) {
            value = ((RadViewComponent)component).getTag().getName();
          }
          else {
            value = StringUtil.shortenTextWithEllipsis(getPropertyValue(component, variable), 30, 5);
          }

          if (!StringUtil.isEmpty(value)) {
            fullTitle.append(title.substring(0, start)).append(value).append(title.substring(end + 1));
          }
        }
      }
    }

    renderer.append(fullTitle.toString());
    renderer.setIcon(metaModel.getIcon());
  }

  @Nullable
  private static String getPropertyValue(RadComponent component, String name) {
    Property property = PropertyTable.findProperty(component.getProperties(), name);
    if (property != null) {
      try {
        return String.valueOf(property.getValue(component));
      }
      catch (Exception e) {
        return null;
      }
    }
    return null;
  }
}