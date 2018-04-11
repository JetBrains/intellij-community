/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl.artifacts;

import com.intellij.util.config.AbstractProperty;

/**
 * @author nik
 */
class SinglePropertyContainer<T extends AbstractProperty> extends AbstractProperty.AbstractPropertyContainer<T> {
  private final T myProperty;
  private Object myValue;

  SinglePropertyContainer(T property, Object value) {
    myProperty = property;
    myValue = value;
  }

  @Override
  protected Object getValueOf(T t) {
    if (myProperty.equals(t)) {
      return myValue;
    }
    throw new IllegalArgumentException("Property " + t.getName() + " not found");
  }

  @Override
  protected void setValueOf(T t, Object value) {
    if (myProperty.equals(t)) {
      myValue = value;
    }
    else {
      throw new IllegalArgumentException("Property " + t.getName() + " not found");
    }
  }

  @Override
  public boolean hasProperty(AbstractProperty property) {
    return myProperty.equals(property);
  }
}
