// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AttributeBinding extends BasePrimitiveBinding {
  private final Class<?> valueClass;

  AttributeBinding(@NotNull MutableAccessor accessor, @Nullable Attribute attribute) {
    super(accessor, attribute == null ? null : attribute.value(), attribute == null ? null : attribute.converter());

    valueClass = ClassUtil.typeToClass(accessor.getGenericType());
  }

  @Override
  public @Nullable Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      return null;
    }

    Converter<Object> converter = getConverter();
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
  public @NotNull Object deserialize(@NotNull Object context, @NotNull Element element) {
    return context;
  }

  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull XmlElement element) {
    return context;
  }

  void set(@NotNull Object host, @NotNull String value) {
    if (myConverter == null) {
      XmlSerializerImpl.doSet(host, value, myAccessor, valueClass);
    }
    else {
      myAccessor.set(host, myConverter.fromString(value));
    }
  }

  public String toString() {
    return "AttributeBinding[" + name + "]";
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
