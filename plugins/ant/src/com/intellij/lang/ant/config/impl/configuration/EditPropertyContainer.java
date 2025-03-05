// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.util.config.AbstractProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
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

  @Override
  protected Object getValueOf(AbstractProperty property) {
    if (myModifications.containsKey(property)) return myModifications.get(property);
    AbstractProperty.AbstractPropertyContainer container = findContainerOf(property);
    if (container == null) return property.getDefault(this);
    Object originalValue = delegateGet(container, property);
    property.copy(originalValue);
    return originalValue;
  }

  @Override
  public boolean hasProperty(AbstractProperty property) {
    return findContainerOf(property) != null;
  }

  @Override
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

  private @Nullable AbstractProperty.AbstractPropertyContainer findContainerOf(AbstractProperty property) {
    if (myParent.hasProperty(property)) return myParent;
    for (AbstractProperty.AbstractPropertyContainer original : myOriginals) {
      if (original.hasProperty(property)) return original;
    }
    return null;
  }
}
