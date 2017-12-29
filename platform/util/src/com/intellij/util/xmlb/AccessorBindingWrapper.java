/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

class AccessorBindingWrapper extends Binding implements MultiNodeBinding {
  protected final Binding myBinding;

  private final boolean myFlat;
  private final Property.Style beanStyle;

  public AccessorBindingWrapper(@NotNull MutableAccessor accessor,
                                @NotNull Binding binding,
                                boolean flat,
                                Property.Style beanStyle) {
    super(accessor);

    myBinding = binding;
    myFlat = flat;
    this.beanStyle = beanStyle;
  }

  public boolean isFlat() {
    return myFlat;
  }

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      throw new XmlSerializationException("Property " + myAccessor + " of object " + o + " (" + o.getClass() + ") must not be null");
    }
    if (myFlat) {
      Element element = (Element)context;
      if (beanStyle == Property.Style.ATTRIBUTE && value instanceof Rectangle) {
        Rectangle bounds = (Rectangle)value;
        LOG.assertTrue(element != null);
        element.setAttribute("x", Integer.toString(bounds.x));
        element.setAttribute("y", Integer.toString(bounds.y));
        element.setAttribute("width", Integer.toString(bounds.width));
        element.setAttribute("height", Integer.toString(bounds.height));
      }
      else {
        ((BeanBinding)myBinding).serializeInto(value, element, filter);
      }
      return null;
    }
    else {
      return myBinding.serialize(value, context, filter);
    }
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }

  @NotNull
  public Object deserialize(@NotNull Object context, @NotNull Element element) {
    Object currentValue = myAccessor.read(context);
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(currentValue, element);
    }
    else {
      Object deserializedValue;
      if (beanStyle == Property.Style.ATTRIBUTE && myBinding instanceof BeanBinding && ((BeanBinding)myBinding).myBeanClass == Rectangle.class) {
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
        deserializedValue = myBinding.deserializeUnsafe(currentValue, element);
      }

      if (currentValue != deserializedValue) {
        myAccessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Nullable
  @Override
  public Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<Element> elements) {
    Object currentValue = myAccessor.read(context);
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(currentValue, elements.get(0));
    }
    else {
      Object deserializedValue = Binding.deserializeList(myBinding, currentValue, elements);
      if (currentValue != deserializedValue) {
        myAccessor.set(context, deserializedValue);
      }
    }
    return context;
  }

  @Override
  public boolean isMulti() {
    return myBinding instanceof MultiNodeBinding && ((MultiNodeBinding)myBinding).isMulti();
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return myBinding.isBoundTo(element);
  }
}
