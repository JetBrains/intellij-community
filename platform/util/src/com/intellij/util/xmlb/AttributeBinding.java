// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import kotlinx.serialization.json.JsonElement;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.xmlb.JsonHelperKt.valueToJson;

final class AttributeBinding implements NestedBinding {
  final Class<?> valueClass;

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
  public @Nullable JsonElement deserializeToJson(@NotNull Element element) {
    return valueToJson(element.getAttributeValue(name), valueClass);
  }

  @Override
  public @NotNull JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    return JsonHelperKt.toJson(bean, accessor, converter);
  }

  @Override
  public void setFromJson(@NotNull Object bean, @NotNull JsonElement data) {
    JsonHelperKt.setFromJson(bean, data, accessor, valueClass, converter);
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public void serialize(@NotNull Object bean, @NotNull Element parent, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return;
    }

    String stringValue;
    if (converter == null) {
      stringValue = XmlSerializerImpl.convertToString(value);
    }
    else {
      stringValue = converter.toString(value);
      if (stringValue == null) {
        return;
      }
    }

    org.jdom.Attribute attribute = new org.jdom.Attribute(true, name, stringValue, Namespace.NO_NAMESPACE);
    parent.setAttribute(attribute);
  }

  @Override
  public @Nullable <T> Object deserialize(@Nullable Object context, @NotNull T element, @NotNull DomAdapter<T> adapter) {
    return context;
  }

  public @NotNull Object deserialize(@NotNull Object context) {
    return context;
  }

  public void setValue(@NotNull Object bean, @Nullable String value) {
    if (converter == null) {
      XmlSerializerImpl.doSet(bean, value, accessor, valueClass);
    }
    else {
      accessor.set(bean, value == null ? null : converter.fromString(value));
    }
  }

  @Override
  public String toString() {
    return "AttributeBinding(name=" + name + ")";
  }

  @Override
  public <T> boolean isBoundTo(@NotNull T element, @NotNull DomAdapter<T> adapter) {
    return false;
  }
}
