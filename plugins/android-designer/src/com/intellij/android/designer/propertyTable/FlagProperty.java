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
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class FlagProperty extends Property<RadViewComponent> {
  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final AttributeDefinition myDefinition;

  public FlagProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    super(null, name);
    myDefinition = definition;
  }

  @Override
  public Property createForNewPresentation() {
    return new FlagProperty(getName(), myDefinition);
  }

  @Override
  public List<Property> getChildren(@Nullable RadViewComponent component) {
    return super.getChildren(component); // TODO: Auto-generated method stub
  }

  @Override
  public Object getValue(RadViewComponent component) throws Exception {
    return "[]";
  }

  @Override
  public void setValue(RadViewComponent component, Object value) throws Exception {
    super.setValue(component, value); // TODO: Auto-generated method stub
  }

  @Override
  public boolean isDefaultValue(RadViewComponent component) throws Exception {
    return super.isDefaultValue(component); // TODO: Auto-generated method stub
  }

  @Override
  public void setDefaultValue(RadViewComponent component) throws Exception {
    super.setDefaultValue(component); // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return null;
  }
}