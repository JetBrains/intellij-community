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
package com.intellij.android.designer.model;

import com.intellij.android.designer.propertyTable.IdProperty;
import com.intellij.android.designer.propertyTable.IncludeLayoutProperty;
import com.intellij.android.designer.propertyTable.editors.ResourceDialog;
import com.intellij.designer.ModuleProvider;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.openapi.ui.DialogWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadIncludeLayout extends RadViewComponent implements IConfigurableComponent {
  @Override
  public String getCreationXml() {
    return "<include android:layout_width=\"wrap_content\"\n" +
           "android:layout_height=\"wrap_content\"\n" +
           "layout=\"" +
           extractClientProperty(IncludeLayoutProperty.NAME) +
           "\"/>";
  }

  public void configure(RadComponent rootComponent) throws Exception {
    ModuleProvider moduleProvider = rootComponent.getClientProperty(ModelParser.MODULE_KEY);
    ResourceDialog dialog = new ResourceDialog(moduleProvider.getModule(), IncludeLayoutProperty.TYPES, null, null);
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      setClientProperty(IncludeLayoutProperty.NAME, dialog.getResourceName());
    }
    else {
      throw new Exception();
    }
  }

  @Override
  public void setProperties(List<Property> properties) {
    if (!properties.isEmpty()) {
      properties = new ArrayList<Property>(properties);
      properties.add(IncludeLayoutProperty.INSTANCE);
      properties.add(IdProperty.INSTANCE);
    }
    super.setProperties(properties);
  }

  @Override
  public int getViewInfoCount() {
    return super.getViewInfoCount(); // TODO: Auto-generated method stub
  }
}