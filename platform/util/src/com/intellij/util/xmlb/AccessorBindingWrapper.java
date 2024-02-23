// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Property;
import kotlin.Unit;
import kotlinx.serialization.json.JsonElement;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
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
    return value == null ? null : binding.toJson(value, filter);
  }

  @Override
  public Object fromJson(@NotNull Object bean, @NotNull JsonElement element) {
    Object currentValue = accessor.read(bean);
    Object value = binding.fromJson(bean, element);
    if (currentValue != value) {
      accessor.set(bean, value);
    }
    return Unit.INSTANCE;
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

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
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
      deserializedValue = binding.deserializeUnsafe(currentValue, element);
    }

    if (currentValue != deserializedValue) {
      accessor.set(context, deserializedValue);
    }
    return context;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
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
        deserializedValue = binding.deserializeUnsafe(currentValue, element);
      }

      if (currentValue != deserializedValue) {
        accessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public @NotNull Object deserializeJdomList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<? extends Element> elements) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, elements.get(0));
    }
    else {
      Object deserializedValue = OptionTagBindingKt.deserializeJdomList(binding, currentValue, elements);
      if (currentValue != deserializedValue) {
        accessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public @NotNull Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<XmlElement> elements) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, elements.get(0));
    }
    else {
      Object deserializedValue = OptionTagBindingKt.deserializeList(binding, currentValue, elements);
      if (currentValue != deserializedValue) {
        accessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public boolean isMulti() {
    return binding instanceof MultiNodeBinding && ((MultiNodeBinding)binding).isMulti();
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    if (binding instanceof MapBinding) {
      return ((MapBinding)binding).isBoundToWithoutProperty(element.getName());
    }
    else {
      return binding.isBoundTo(element);
    }
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    if (binding instanceof MapBinding) {
      return ((MapBinding)binding).isBoundToWithoutProperty(element.name);
    }
    else {
      return binding.isBoundTo(element);
    }
  }

  @Override
  public String toString() {
    return binding.toString();
  }
}
