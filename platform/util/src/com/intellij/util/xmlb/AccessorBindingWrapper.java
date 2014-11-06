/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xmlb;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AccessorBindingWrapper implements Binding {
  private final Accessor myAccessor;
  private final Binding myBinding;

  public AccessorBindingWrapper(@NotNull Accessor accessor, @NotNull Binding binding) {
    myAccessor = accessor;
    myBinding = binding;
  }

  @Nullable
  @Override
  public Object serialize(Object o, @Nullable Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      throw new XmlSerializationException("Property " + myAccessor + " of object " + o + " (" + o.getClass() + ") must not be null");
    }
    return myBinding.serialize(value, context, filter);
  }

  @Override
  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    Object currentValue = myAccessor.read(context);
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(currentValue, (Element)nodes[0], null);
    }
    else {
      Object deserializedValue = myBinding.deserialize(currentValue, nodes);
      if (currentValue != deserializedValue) {
        myAccessor.write(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return myBinding.isBoundTo(node);
  }

  @Override
  public Class getBoundNodeType() {
    return myBinding.getBoundNodeType();
  }

  @Override
  public void init() {
  }
}
