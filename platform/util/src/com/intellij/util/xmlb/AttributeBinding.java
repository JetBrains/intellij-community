/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AttributeBinding extends BasePrimitiveBinding {
  private final Class<?> valueClass;

  public AttributeBinding(@NotNull MutableAccessor accessor, @Nullable Attribute attribute) {
    super(accessor, attribute == null ? null : attribute.value(), attribute == null ? null : attribute.converter());

    valueClass = XmlSerializerImpl.typeToClass(accessor.getGenericType());
  }

  @Override
  @Nullable
  public Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
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
    return new org.jdom.Attribute(myName, stringValue);
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
    return "AttributeBinding[" + myName + "]";
  }
}
