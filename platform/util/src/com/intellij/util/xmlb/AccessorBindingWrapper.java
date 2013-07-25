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

package com.intellij.util.xmlb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AccessorBindingWrapper implements Binding {
  private final Accessor myAccessor;
  private final Binding myBinding;


  public AccessorBindingWrapper(final Accessor accessor, final Binding binding) {
    myAccessor = accessor;
    myBinding = binding;
  }

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      throw new XmlSerializationException("Property " + myAccessor + " of object " + o + " (" + o.getClass() + ") must not be null");
    }
    return myBinding.serialize(value, context, filter);
  }

  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    myAccessor.write(context, myBinding.deserialize(myAccessor.read(context), nodes));
    return context;
  }

  public boolean isBoundTo(Object node) {
    return myBinding.isBoundTo(node);
  }

  public Class getBoundNodeType() {
    return myBinding.getBoundNodeType();
  }

  public void init() {
  }
}
