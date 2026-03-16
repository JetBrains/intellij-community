// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Property;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonNull;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;
import java.util.List;

final class AccessorBindingWrapper implements MultiNodeBinding, NestedBinding {
  private final Binding binding;
  private final MutableAccessor accessor;

  final boolean isFlat;
  private final Property.Style beanStyle;

  AccessorBindingWrapper(@NotNull MutableAccessor accessor, @NotNull Binding binding, boolean isFlat, Property.Style beanStyle) {
    this.accessor = accessor;
    this.binding = binding;
    this.isFlat = isFlat;
    this.beanStyle = beanStyle;
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public @Nullable JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    return value == null ? JsonNull.INSTANCE : binding.toJson(value, filter);
  }

  @Override
  public void setFromJson(@NotNull Object bean, @NotNull JsonElement element) {
    Object currentValue = accessor.read(bean);
    Object value = ((RootBinding)binding).fromJson(currentValue, element);
    if (currentValue != value) {
      accessor.set(bean, value);
    }
  }

  @Override
  public void serialize(@NotNull Object bean, @NotNull Element parent, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return;
    }

    if (isFlat) {
      if (beanStyle == Property.Style.ATTRIBUTE && value instanceof Rectangle) {
        Rectangle bounds = (Rectangle)value;
        assert parent != null;
        parent.setAttribute("x", Integer.toString(bounds.x));
        parent.setAttribute("y", Integer.toString(bounds.y));
        parent.setAttribute("width", Integer.toString(bounds.width));
        parent.setAttribute("height", Integer.toString(bounds.height));
      }
      else {
        ((BeanBinding)binding).serializeProperties(value, parent, filter);
      }
    }
    else {
      binding.serialize(value, parent, filter);
    }
  }

  @Override
  public @Nullable <T> Object deserialize(@Nullable Object context, @NotNull T element, @NotNull DomAdapter<T> adapter) {
    if (adapter == JdomAdapter.INSTANCE) {
      return deserializeUnsafe(context, (Element)element);
    }
    else {
      return deserializeUnsafe(context, (XmlElement)element);
    }
  }

  @Override
  public @Nullable JsonElement deserializeToJson(@NotNull Element element) {
    return ((RootBinding)binding).deserializeToJson(element);
  }

  @SuppressWarnings("DuplicatedCode")
  Object deserializeUnsafe(Object context, @NotNull Element element) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, element);
      return context;
    }

    Object deserializedValue;
    if (beanStyle == Property.Style.ATTRIBUTE && binding instanceof BeanBinding && ((BeanBinding)binding).beanClass == Rectangle.class) {
      String xA = element.getAttributeValue("x");
      String yA = element.getAttributeValue("y");
      String wA = element.getAttributeValue("width");
      String hA = element.getAttributeValue("height");

      if (xA != null && yA != null && wA != null && hA != null) {
        int x = StringUtilRt.parseInt(xA, 0);
        int y = StringUtilRt.parseInt(yA, 0);
        int h = StringUtilRt.parseInt(hA, 0);
        int w = StringUtilRt.parseInt(wA, 0);
        deserializedValue = new Rectangle(x, y, w, h);
      }
      else {
        return context;
      }
    }
    else {
      deserializedValue = binding.deserialize(currentValue, element, JdomAdapter.INSTANCE);
    }

    if (currentValue != deserializedValue) {
      accessor.set(context, deserializedValue);
    }
    return context;
  }

  @SuppressWarnings("DuplicatedCode")
  Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, element);
    }
    else {
      Object deserializedValue;
      if (beanStyle == Property.Style.ATTRIBUTE && binding instanceof BeanBinding && ((BeanBinding)binding).beanClass == Rectangle.class) {
        String xA = element.getAttributeValue("x");
        String yA = element.getAttributeValue("y");
        String wA = element.getAttributeValue("width");
        String hA = element.getAttributeValue("height");

        if (xA != null && yA != null && wA != null && hA != null) {
          int x = StringUtilRt.parseInt(xA, 0);
          int y = StringUtilRt.parseInt(yA, 0);
          int h = StringUtilRt.parseInt(hA, 0);
          int w = StringUtilRt.parseInt(wA, 0);
          deserializedValue = new Rectangle(x, y, w, h);
        }
        else {
          return context;
        }
      }
      else {
        deserializedValue = binding.deserialize(currentValue, element, XmlDomAdapter.INSTANCE);
      }

      if (currentValue != deserializedValue) {
        accessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public @Nullable <T> Object deserializeList(@Nullable Object currentValue, @NotNull List<? extends T> elements, @NotNull DomAdapter<T> adapter) {
    assert currentValue != null;
    Object value = accessor.read(currentValue);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(value, elements.get(0), adapter);
    }
    else {
      Object deserializedValue = MapBindingKt.deserializeList(binding, value, elements, adapter);
      if (value != deserializedValue) {
        accessor.set(currentValue, deserializedValue);
      }
    }
    return null;
  }

  @Override
  public boolean isSurroundWithTag() {
    return false;
  }

  @Override
  public @NotNull JsonElement doDeserializeListToJson(@NotNull List<? extends Element> elements) {
    return ((MultiNodeBinding)binding).deserializeListToJson(elements);
  }

  @Override
  public boolean isMulti() {
    return binding instanceof MultiNodeBinding && ((MultiNodeBinding)binding).isMulti();
  }

  @Override
  public <T> boolean isBoundTo(@NotNull T element, @NotNull DomAdapter<T> adapter) {
    if (binding instanceof MapBinding) {
      return ((MapBinding)binding).isBoundToWithoutProperty(adapter.getName(element));
    }
    else {
      return binding.isBoundTo(element, adapter);
    }
  }

  @Override
  public String toString() {
    return binding.toString();
  }
}
