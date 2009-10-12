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
