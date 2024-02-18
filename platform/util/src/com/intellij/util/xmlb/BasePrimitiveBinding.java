// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;

@ApiStatus.Internal
abstract class BasePrimitiveBinding implements PrimitiveValueBinding {
  protected final MutableAccessor accessor;
  protected final String name;

  protected final @Nullable Converter<Object> converter;

  protected @Nullable Binding binding;

  protected BasePrimitiveBinding(
    @NotNull MutableAccessor accessor,
    @Nullable String suggestedName,
    @SuppressWarnings("rawtypes") @Nullable Class<? extends Converter> converterClass
  ) {
    this.accessor = accessor;

    name = (suggestedName == null || suggestedName.isEmpty()) ? this.accessor.getName() : suggestedName;
    //noinspection unchecked
    converter = converterClass == null || converterClass == Converter.class ? null : ReflectionUtil.newInstance(converterClass);
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public final void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    if (converter == null && !(this instanceof AttributeBinding)) {
      binding = serializer.getBinding(accessor);
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
    return converter;
  }

  static void addContent(@NotNull Element targetElement, Object node) {
    if (node instanceof Content) {
      targetElement.addContent((Content)node);
    }
    else if (node instanceof List) {
      //noinspection unchecked,rawtypes
      targetElement.addContent((List)node);
    }
    else {
      throw new IllegalArgumentException("Wrong node: " + node);
    }
  }
}