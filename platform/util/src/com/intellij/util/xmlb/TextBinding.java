// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.util.serialization.ClassUtil;
import com.intellij.util.serialization.MutableAccessor;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TextBinding extends Binding {
  private final Class<?> valueClass;

  TextBinding(@NotNull MutableAccessor accessor) {
    super(accessor);

    valueClass = ClassUtil.typeToClass(accessor.getGenericType());
  }

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    return value == null ? null : new Text(XmlSerializerImpl.convertToString(value));
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    return context;
  }

  void set(@NotNull Object context, @NotNull String value) {
    XmlSerializerImpl.doSet(context, value, myAccessor, valueClass);
  }
}
