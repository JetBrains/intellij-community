package com.intellij.lang.ant.config.impl.configuration;

import com.intellij.util.config.AbstractProperty;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class EditPropertyContainer extends AbstractProperty.AbstractPropertyContainer {
  private final AbstractProperty.AbstractPropertyContainer[] myOriginals;
  private final Map<AbstractProperty, Object> myModifications = new HashMap<AbstractProperty, Object>();
  private final AbstractProperty.AbstractPropertyContainer myParent;

  public EditPropertyContainer(AbstractProperty.AbstractPropertyContainer original) {
    this(new AbstractProperty.AbstractPropertyContainer[]{original});
  }

  public EditPropertyContainer(AbstractProperty.AbstractPropertyContainer[] originals) {
    this(AbstractProperty.AbstractPropertyContainer.EMPTY, originals);
  }

  private EditPropertyContainer(AbstractProperty.AbstractPropertyContainer parentEditor, AbstractProperty.AbstractPropertyContainer[] originals) {
    myOriginals = originals;
    if (parentEditor == null) throw new NullPointerException("parentEditor");
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
