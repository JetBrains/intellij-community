/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.util.config.AbstractProperty;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class EditPropertyContainer extends AbstractProperty.AbstractPropertyContainer {
  private final AbstractProperty.AbstractPropertyContainer[] myOriginals;
  private final Map<AbstractProperty, Object> myModifications = new HashMap<>();
  private final AbstractProperty.AbstractPropertyContainer myParent;

  public EditPropertyContainer(AbstractProperty.AbstractPropertyContainer original) {
    this(new AbstractProperty.AbstractPropertyContainer[]{original});
  }

  public EditPropertyContainer(AbstractProperty.AbstractPropertyContainer[] originals) {
    this(AbstractProperty.AbstractPropertyContainer.EMPTY, originals);
  }

  private EditPropertyContainer(@NotNull AbstractProperty.AbstractPropertyContainer parentEditor, AbstractProperty.AbstractPropertyContainer[] originals) {
    myOriginals = originals;
    myParent = parentEditor;
  }

  public EditPropertyContainer(EditPropertyContainer parentEditor, AbstractProperty.AbstractPropertyContainer original) {
    this(parentEditor, new AbstractProperty.AbstractPropertyContainer[]{original});
  }

  protected Object getValueOf(AbstractProperty property) {
    if (myModifications.containsKey(property)) return myModifications.get(property);
    AbstractProperty.AbstractPropertyContainer container = findContainerOf(property);
    if (container == null) return property.getDefault(this);
    Object originalValue = delegateGet(container, property);
    property.copy(originalValue);
    return originalValue;
  }

  public boolean hasProperty(AbstractProperty property) {
    return findContainerOf(property) != null;
  }

  protected void setValueOf(AbstractProperty property, Object value) {
    if (myParent.hasProperty(property)) delegateSet(myParent, property, value);
    else myModifications.put(property, value);
  }

  public void apply() {
    for (AbstractProperty property : myModifications.keySet()) {
      AbstractProperty.AbstractPropertyContainer container = findContainerOf(property);
      if (container != null) delegateSet(container, property, myModifications.get(property));
    }
    myModifications.clear();
  }

  @Nullable
  private AbstractProperty.AbstractPropertyContainer findContainerOf(AbstractProperty property) {
    if (myParent.hasProperty(property)) return myParent;
    for (AbstractProperty.AbstractPropertyContainer original : myOriginals) {
      if (original.hasProperty(property)) return original;
    }
    return null;
  }
}
