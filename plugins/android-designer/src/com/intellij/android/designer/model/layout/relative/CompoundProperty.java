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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.Property;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class CompoundProperty extends com.intellij.android.designer.propertyTable.CompoundProperty {
  private final String myJavadocText;

  public CompoundProperty(@NotNull String name, String javadocText) {
    super(name);
    myJavadocText = javadocText;
    setImportant(true);
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    StringBuilder value = new StringBuilder("[");
    int index = 0;
    for (Property<RadViewComponent> child : getChildren(component)) {
      if (!child.isDefaultValue(component)) {
        if (index++ > 0) {
          value.append(", ");
        }
        value.append(child.getName());
      }
    }
    return value.append("]").toString();
  }

  @Override
  public String getJavadocText() {
    return myJavadocText;
  }
}