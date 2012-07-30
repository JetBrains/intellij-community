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
package com.intellij.android.designer.propertyTable;

import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * @author Alexander Lobas
 */
public class CompoundDimensionProperty extends CompoundProperty {
  private ResourceRenderer myRenderer = new ResourceRenderer(EnumSet.of(AttributeFormat.Dimension)) {
    @Override
    protected void formatValue(RadComponent component, String value) {
      myColoredComponent.append("[");
      if (!StringUtil.isEmpty(value)) {
        int index = 0;
        for (String childValue : StringUtil.split(value, ",")) {
          if (index++ > 0) {
            myColoredComponent.append(", ");
          }
          childValue = childValue.trim();
          if (childValue.length() > 0) {
            if (childValue.equals("?")) {
              myColoredComponent.append("?", SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
            }
            else {
              super.formatValue(component, childValue);
            }
          }
        }
      }
      myColoredComponent.append("]");
    }
  };

  public CompoundDimensionProperty(@NotNull String name) {
    super(name);
  }

  @Override
  protected CompoundProperty createForNewPresentation(@NotNull String name) {
    return new CompoundDimensionProperty(name);
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }
}