package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ExternalizablePropertyContainer;

public class CompositePropertyContainer extends AbstractProperty.AbstractPropertyContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.CompositePropertyContainer");
  private final AbstractProperty.AbstractPropertyContainer[] myContainers;

  public CompositePropertyContainer(AbstractProperty.AbstractPropertyContainer[] containers) {
    myContainers = containers;
  }

  public Object getValueOf(AbstractProperty property) {
    return property.get(containerOf(property));
  }

  public void setValueOf(AbstractProperty property, Object value) {
    property.set(containerOf(property), value);
  }

  public boolean hasProperty(AbstractProperty property) {
    for (AbstractProperty.AbstractPropertyContainer container : myContainers) {
      if (container.hasProperty(property)) return true;
    }
    return false;
  }

  private AbstractProperty.AbstractPropertyContainer containerOf(AbstractProperty property) {
    for (AbstractProperty.AbstractPropertyContainer container : myContainers) {
      if (container.hasProperty(property)) return container;
    }
    if (ApplicationManager.getApplication() != null) LOG.error("Unknown property: " + property.getName());
    return new ExternalizablePropertyContainer();
  }
}
