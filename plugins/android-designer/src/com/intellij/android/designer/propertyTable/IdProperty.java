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
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class IdProperty extends AttributeProperty {
  public static final Property INSTANCE = new IdProperty();

  private IdProperty() {
    this("id", new AttributeDefinition("id", Arrays.asList(AttributeFormat.Reference)));
    setImportant(true);
  }

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
  public void setValue(@NotNull final RadViewComponent component, final Object value) throws Exception {
    IdManager idManager = IdManager.get(component);
    final String oldId = component.getId();

    idManager.removeComponent(component, false);
    super.setValue(component, value);
    idManager.addComponent(component);

    if (oldId != null && value != null && !oldId.equals(value)) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Pair<String, String> replace;
          if (oldId.startsWith("@android:id/")) {
            replace = new Pair<String, String>(oldId, oldId);
          }
          else {
            String idValue = oldId.substring(oldId.indexOf('/') + 1);
            replace = new Pair<String, String>("@id/" + idValue, "@+id/" + idValue);
          }

          IdManager.replaceIds((RadViewComponent)component.getRoot(),
                               Arrays.asList(new Pair<Pair<String, String>, String>(replace, (String)value)));
        }
      });
    }
  }

  @Override
  public boolean availableFor(List<PropertiesContainer> components) {
    return false;
  }
}