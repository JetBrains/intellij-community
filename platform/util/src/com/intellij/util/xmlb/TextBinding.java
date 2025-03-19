// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import kotlinx.serialization.json.JsonElement;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.xmlb.JsonHelperKt.valueToJson;

final class TextBinding implements NestedBinding {
  private final Class<?> valueClass;
  private final MutableAccessor accessor;

  TextBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;
    valueClass = ClassUtil.typeToClass(accessor.getGenericType());
  }

  @Override
  public @Nullable JsonElement deserializeToJson(@NotNull Element element) {
    return valueToJson(element.getText(), valueClass);
  }

  @Override
  public @Nullable JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    return JsonHelperKt.toJson(bean, accessor, null);
  }

  @Override
  public void setFromJson(@NotNull Object bean, @NotNull JsonElement element) {
    JsonHelperKt.setFromJson(bean, element, accessor, valueClass, null);
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
  public <T> boolean isBoundTo(@NotNull T element, @NotNull DomAdapter<T> adapter) {
    return false;
  }

  @Override
  public @Nullable <T> Object deserialize(@Nullable Object context, @NotNull T element, @NotNull DomAdapter<T> adapter) {
    return context;
  }

  void setValue(@NotNull Object context, @Nullable String value) {
    XmlSerializerImpl.doSet(context, value, accessor, valueClass);
  }
}
