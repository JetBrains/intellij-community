// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.XmlElement;
import com.intellij.util.xmlb.annotations.Property;
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
  public @Nullable Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = accessor.read(o);
    if (value == null) {
      return null;
    }
    else if (isFlat) {
      Element element = (Element)context;
      if (beanStyle == Property.Style.ATTRIBUTE && value instanceof Rectangle) {
        Rectangle bounds = (Rectangle)value;
        assert element != null;
        element.setAttribute("x", Integer.toString(bounds.x));
        element.setAttribute("y", Integer.toString(bounds.y));
        element.setAttribute("width", Integer.toString(bounds.width));
        element.setAttribute("height", Integer.toString(bounds.height));
      }
      else {
        ((BeanBinding)binding).serializeInto(value, element, filter);
      }
      return null;
    }
    else {
      return binding.serialize(value, context, filter);
    }
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, element);
    }
    else {
      Object deserializedValue;
      if (beanStyle == Property.Style.ATTRIBUTE && binding instanceof BeanBinding && ((BeanBinding)binding).myBeanClass == Rectangle.class) {
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

  @SuppressWarnings("DuplicatedCode")
  @Override
  public Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, element);
    }
    else {
      Object deserializedValue;
      if (beanStyle == Property.Style.ATTRIBUTE && binding instanceof BeanBinding && ((BeanBinding)binding).myBeanClass == Rectangle.class) {
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
  public @NotNull Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<Element> elements) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, elements.get(0));
    }
    else {
      Object deserializedValue = Binding.deserializeList(binding, currentValue, elements);
      if (currentValue != deserializedValue) {
        accessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public @NotNull Object deserializeList2(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<XmlElement> elements) {
    Object currentValue = accessor.read(context);
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(currentValue, elements.get(0));
    }
    else {
      Object deserializedValue = Binding.deserializeList2(binding, currentValue, elements);
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
