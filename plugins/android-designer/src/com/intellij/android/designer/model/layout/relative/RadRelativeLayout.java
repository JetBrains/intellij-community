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
import com.intellij.android.designer.model.PropertyParser;
import com.intellij.android.designer.model.RadViewLayoutWithData;
import com.intellij.android.designer.propertyTable.CompoundProperty;
import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.propertyTable.Property;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadRelativeLayout extends RadViewLayoutWithData {
  private static final String[] LAYOUT_PARAMS = {"RelativeLayout_Layout", "ViewGroup_MarginLayout"};

  @NotNull
  @Override
  public String[] getLayoutParams() {
    return LAYOUT_PARAMS;
  }

  @Override
  public void configureProperties(List<Property> properties) {
    CompoundProperty alignParent = new CompoundProperty("layout:alignParent");
    alignParent.setImportant(true);
    PropertyParser.moveProperties(properties, alignParent,
                                  "layout:alignParentTop", "top",
                                  "layout:alignParentLeft", "left",
                                  "layout:alignParentBottom", "bottom",
                                  "layout:alignParentRight", "right",
                                  "layout:alignWithParentIfMissing", "missing");
    properties.add(alignParent);
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
    return null;
  }
}