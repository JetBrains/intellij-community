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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.IPropertyDecorator;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class CompoundProperty extends Property<RadViewComponent> implements IPropertyDecorator, IXmlAttributeLocator {
  private final List<Property<RadViewComponent>> myChildren = new ArrayList<Property<RadViewComponent>>();
  private PropertyRenderer myRenderer;

  public CompoundProperty(@NotNull String name) {
    super(null, name);
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    CompoundProperty property = createForNewPresentation(name);
    List<Property<RadViewComponent>> children = property.getChildren(null);
    for (Property<RadViewComponent> childProperty : myChildren) {
      children.add(childProperty.createForNewPresentation(property, childProperty.getName()));
    }
    return property;
  }

  public void decorate(@NotNull MetaModel model) {
    String name = getName();
    model.decorate0(this, name);
    for (Property<RadViewComponent> childProperty : myChildren) {
      model.decorate(childProperty, name + "." + childProperty.getName());
    }
  }

  @NotNull
  @Override
  public List<Property<RadViewComponent>> getChildren(@Nullable RadViewComponent component) {
    return myChildren;
  }

  protected CompoundProperty createForNewPresentation(@NotNull String name) {
    return new CompoundProperty(name);
  }

  @Override
  public Object getValue(RadViewComponent component) throws Exception {
    StringBuilder value = new StringBuilder();
    int index = 0;
    int empty = 0;
    for (Property<RadViewComponent> childProperty : myChildren) {
      if (index++ > 0) {
        value.append(", ");
      }
      String childValue = (String)childProperty.getValue(component);
      if (StringUtil.isEmpty(childValue)) {
        empty++;
        value.append("?");
      }
      else {
        value.append(childValue);
      }
    }
    if (empty == myChildren.size()) {
      return "";
    }
    return value.toString();
  }

  @Override
  public boolean isDefaultValue(RadViewComponent component) throws Exception {
    for (Property<RadViewComponent> childProperty : myChildren) {
      if (!childProperty.isDefaultValue(component)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setDefaultValue(RadViewComponent component) throws Exception {
    for (Property<RadViewComponent> childProperty : myChildren) {
      childProperty.setDefaultValue(component);
    }
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer(null);
    }
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return null;
  }

  @Override
  public String getJavadocText() {
    return myChildren.isEmpty() ? null : myChildren.get(0).getJavadocText();
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    for (Property<RadViewComponent> childProperty : myChildren) {
      if (((IXmlAttributeLocator)childProperty).checkAttribute(component, attribute)) {
        return true;
      }
    }
    return false;
  }
}