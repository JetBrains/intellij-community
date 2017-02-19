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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

abstract class BasePrimitiveBinding extends Binding {
  protected final String myName;

  @Nullable
  protected final Converter<Object> myConverter;

  @Nullable
  protected Binding myBinding;

  protected BasePrimitiveBinding(@NotNull MutableAccessor accessor, @Nullable String suggestedName, @Nullable Class<? extends Converter> converterClass) {
    super(accessor);

    myName = StringUtil.isEmpty(suggestedName) ? myAccessor.getName() : suggestedName;
    //noinspection unchecked
    myConverter = converterClass == null || converterClass == Converter.class ? null : ReflectionUtil.newInstance(converterClass);
  }

  @Override
  public final void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    if (myConverter == null && !(this instanceof AttributeBinding)) {
      myBinding = serializer.getBinding(myAccessor);
    }
  }

  @Nullable
  @Override
  public final Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    return serialize(o, filter);
  }

  @Nullable
  public abstract Object serialize(@NotNull Object o, @Nullable SerializationFilter filter);

  @NotNull
  public Object deserialize(@NotNull Object context, @NotNull Element element) {
    return context;
  }

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }
}