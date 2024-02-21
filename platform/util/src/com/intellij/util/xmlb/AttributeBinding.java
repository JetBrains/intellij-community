// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AttributeBinding implements PrimitiveValueBinding {
  private final Class<?> valueClass;

  private final MutableAccessor accessor;
  final String name;

  private final @Nullable Converter<Object> converter;

  AttributeBinding(@NotNull MutableAccessor accessor, @Nullable Attribute attribute) {
    this.accessor = accessor;
    String suggestedName = attribute == null ? null : attribute.value();
    name = (suggestedName == null || suggestedName.isEmpty()) ? accessor.getName() : suggestedName;

    @SuppressWarnings("rawtypes")
    Class<? extends Converter> converterClass = attribute == null ? null : attribute.converter();
    //noinspection unchecked
    converter = converterClass == null || converterClass == Converter.class ? null : ReflectionUtil.newInstance(converterClass, false);
    valueClass = ClassUtil.typeToClass(accessor.getGenericType());
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public @Nullable Object serialize(@NotNull Object bean, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return null;
    }

    String stringValue;
    if (converter == null) {
      stringValue = XmlSerializerImpl.convertToString(value);
    }
    else {
      stringValue = converter.toString(value);
      if (stringValue == null) {
        return null;
      }
    }
    return new org.jdom.Attribute(name, stringValue);
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context);
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    return context;
  }

  public @NotNull Object deserialize(@NotNull Object context) {
    return context;
  }

  @Override
  public void setValue(@NotNull Object bean, @Nullable String value) {
    if (converter == null) {
      XmlSerializerImpl.doSet(bean, value, accessor, valueClass);
    }
    else {
      accessor.set(bean, value == null ? null : converter.fromString(value));
    }
  }

  public String toString() {
    return "AttributeBinding(name=" + name + ")";
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return false;
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    return false;
  }
}
