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
import com.intellij.designer.propertyTable.Property;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadIncludeComponent extends RadViewComponent {
  private final List<Property> myProperties = new ArrayList<Property>();

  public RadIncludeComponent() {
    IdProperty idProperty = new IdProperty("id", new AttributeDefinition("id", Arrays.asList(AttributeFormat.Reference)));
    idProperty.setImportant(true);
    myProperties.add(idProperty);
  }

  @Override
  public void setProperties(List<Property> properties) {
    if (!properties.isEmpty()) {
      properties = new ArrayList<Property>(properties);
      properties.addAll(myProperties);
    }
    super.setProperties(properties);
  }
}