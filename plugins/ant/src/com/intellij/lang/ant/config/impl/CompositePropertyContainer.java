/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import java.util.Arrays;

public class CompositePropertyContainer extends AbstractProperty.AbstractPropertyContainer {
  private static final Logger LOG = Logger.getInstance(CompositePropertyContainer.class);
  private final AbstractProperty.AbstractPropertyContainer[] myContainers;

  public CompositePropertyContainer(AbstractProperty.AbstractPropertyContainer[] containers) {
    myContainers = containers;
  }

  @Override
  public Object getValueOf(AbstractProperty property) {
    return property.get(containerOf(property));
  }

  @Override
  public void setValueOf(AbstractProperty property, Object value) {
    property.set(containerOf(property), value);
  }

  @Override
  public boolean hasProperty(AbstractProperty property) {
    return Arrays.stream(myContainers).anyMatch(container -> container.hasProperty(property));
  }

  private AbstractProperty.AbstractPropertyContainer containerOf(AbstractProperty property) {
    for (AbstractProperty.AbstractPropertyContainer container : myContainers) {
      if (container.hasProperty(property)) return container;
    }
    if (ApplicationManager.getApplication() != null) LOG.error("Unknown property: " + property.getName());
    return new ExternalizablePropertyContainer();
  }
}
