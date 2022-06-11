// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

abstract class BasePrimitiveBinding implements NestedBinding {
  protected final MutableAccessor myAccessor;
  protected final String name;

  protected final @Nullable Converter<Object> myConverter;

  protected @Nullable Binding myBinding;

  protected BasePrimitiveBinding(@NotNull MutableAccessor accessor, @Nullable String suggestedName, @Nullable Class<? extends Converter> converterClass) {
    myAccessor = accessor;

    name = StringUtil.isEmpty(suggestedName) ? myAccessor.getName() : suggestedName;
    //noinspection unchecked
    myConverter = converterClass == null || converterClass == Converter.class ? null : ReflectionUtil.newInstance(converterClass);
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return myAccessor;
  }

  @Override
  public final void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    if (myConverter == null && !(this instanceof AttributeBinding)) {
      myBinding = serializer.getBinding(myAccessor);
    }
  }

  @Override
  public final @Nullable Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    return serialize(o, filter);
  }

  public abstract @Nullable Object serialize(@NotNull Object o, @Nullable SerializationFilter filter);

  public abstract @NotNull Object deserialize(@NotNull Object context, @NotNull Element element);

  public abstract @NotNull Object deserialize(@NotNull Object context, @NotNull XmlElement element);

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    return deserialize(context, element);
  }

  protected final @Nullable Converter<Object> getConverter() {
    return myConverter;
  }
}