// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import kotlin.Unit;
import kotlinx.serialization.json.JsonElement;
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
  public @Nullable JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    return JsonHelperKt.toJson(bean, accessor, null);
  }

  @Override
  public Object fromJson(@NotNull Object bean, @NotNull JsonElement element) {
    JsonHelperKt.fromJson(bean, element, accessor, valueClass, null);
    return Unit.INSTANCE;
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public void serialize(@NotNull Object bean, @NotNull Element parent, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value != null) {
      parent.addContent(new Text(true, XmlSerializerImpl.convertToString(value)));
    }
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
