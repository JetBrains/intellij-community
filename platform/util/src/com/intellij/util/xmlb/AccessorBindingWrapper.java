/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.List;

class AccessorBindingWrapper extends Binding implements MultiNodeBinding {
  private final Binding myBinding;

  private final boolean myFlat;

  public AccessorBindingWrapper(@NotNull MutableAccessor accessor, @NotNull Binding binding, boolean flat) {
    super(accessor);

    myBinding = binding;
    myFlat = flat;
  }

  public boolean isFlat() {
    return myFlat;
  }

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      throw new XmlSerializationException("Property " + myAccessor + " of object " + o + " (" + o.getClass() + ") must not be null");
    }
    if (myFlat) {
      ((BeanBinding)myBinding).serializeInto(value, (Element)context, filter);
      return null;
    }
    else {
      return myBinding.serialize(value, context, filter);
    }
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }

  @NotNull
  public Object deserialize(@NotNull Object context, @NotNull Element element) {
    Object currentValue = myAccessor.read(context);
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(currentValue, element);
    }
    else {
      Object deserializedValue = myBinding.deserializeUnsafe(currentValue, element);
      if (currentValue != deserializedValue) {
        myAccessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Nullable
  @Override
  public Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<Element> elements) {
    Object currentValue = myAccessor.read(context);
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(currentValue, elements.get(0));
    }
    else {
      Object deserializedValue = Binding.deserializeList(myBinding, currentValue, elements);
      if (currentValue != deserializedValue) {
        myAccessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public boolean isMulti() {
    return myBinding instanceof MultiNodeBinding && ((MultiNodeBinding)myBinding).isMulti();
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return myBinding.isBoundTo(element);
  }
}
