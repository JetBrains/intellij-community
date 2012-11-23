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
import com.intellij.designer.model.Property;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class AttributePropertyWithDefault extends AttributeProperty {
  private final String myDefaultValue;

  public AttributePropertyWithDefault(@NotNull String name, @NotNull AttributeDefinition definition, @NotNull String defaultValue) {
    this(null, name, definition, defaultValue);
  }

  public AttributePropertyWithDefault(@Nullable Property parent,
                                      @NotNull String name,
                                      @NotNull AttributeDefinition definition,
                                      @NotNull String defaultValue) {
    super(parent, name, definition);
    myDefaultValue = defaultValue;
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return new AttributePropertyWithDefault(parent, name, myDefinition, myDefaultValue);
  }

  @Override
  public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
    return myDefaultValue.equals(getValue(component));
  }

  @Override
  public void setDefaultValue(@NotNull RadViewComponent component) throws Exception {
    super.setValue(component, myDefaultValue);
  }

  @Override
  public void setValue(@NotNull RadViewComponent component, Object value) throws Exception {
    if (StringUtil.isEmpty((String)value)) {
      value = myDefaultValue;
    }
    super.setValue(component, value);
  }
}