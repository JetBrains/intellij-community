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

import com.intellij.android.designer.model.IdManager;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class IdProperty extends AttributeProperty {
  public IdProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    super(name, definition);
  }

  public IdProperty(@Nullable Property parent, @NotNull String name, @NotNull AttributeDefinition definition) {
    super(parent, name, definition);
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return new IdProperty(parent, name, myDefinition);
  }

  @Override
  public void setValue(RadViewComponent component, Object value) throws Exception {
    // TODO: rename all references

    IdManager idManager = IdManager.get(component);
    idManager.removeComponent(component, false);

    super.setValue(component, value);

    if (!StringUtil.isEmpty((String)value)) {
      idManager.addComponent(component);
    }
  }

  @Override
  public boolean availableFor(List<RadComponent> components) {
    return false;
  }
}