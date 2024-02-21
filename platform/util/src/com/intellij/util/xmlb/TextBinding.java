// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TextBinding implements PrimitiveValueBinding {
  private final Class<?> valueClass;
  private final MutableAccessor accessor;

  TextBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;
    valueClass = ClassUtil.typeToClass(accessor.getGenericType());
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public @Nullable Object serialize(@NotNull Object bean, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    return value == null ? null : new Text(XmlSerializerImpl.convertToString(value));
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return false;
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    return false;
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    return context;
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    return context;
  }

  @Override
  public void setValue(@NotNull Object context, @Nullable String value) {
    XmlSerializerImpl.doSet(context, value, accessor, valueClass);
  }
}
