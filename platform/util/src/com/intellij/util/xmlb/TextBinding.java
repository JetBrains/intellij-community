// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class TextBinding implements NestedBinding {
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
  public @Nullable Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = accessor.read(o);
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

  void set(@NotNull Object context, @NotNull String value) {
    XmlSerializerImpl.doSet(context, value, accessor, valueClass);
  }
}
