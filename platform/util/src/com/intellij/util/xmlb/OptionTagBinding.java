/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class OptionTagBinding extends BasePrimitiveBinding {
  private final String myTagName;
  private final String myNameAttribute;
  private final String myValueAttribute;

  public OptionTagBinding(@NotNull MutableAccessor accessor, @Nullable OptionTag optionTag) {
    super(accessor, optionTag == null ? null : optionTag.value(), optionTag == null ? null : optionTag.converter());

    if (optionTag == null) {
      myTagName = Constants.OPTION;
      myNameAttribute = Constants.NAME;
      myValueAttribute = Constants.VALUE;
    }
    else {
      myNameAttribute = optionTag.nameAttribute();
      myValueAttribute = optionTag.valueAttribute();

      String tagName = optionTag.tag();
      if (StringUtil.isEmpty(myNameAttribute) && Constants.OPTION.equals(tagName)) {
        tagName = myAccessor.getName();
      }
      myTagName = tagName;
    }
  }

  @Override
  @Nullable
  public Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    Element targetElement = new Element(myTagName);

    if (!StringUtil.isEmpty(myNameAttribute)) {
      targetElement.setAttribute(myNameAttribute, myName);
    }

    if (value == null) {
      return targetElement;
    }

    Converter<Object> converter = getConverter();
    if (converter == null) {
      if (myBinding == null) {
        targetElement.setAttribute(myValueAttribute, XmlSerializerImpl.removeControlChars(XmlSerializerImpl.convertToString(value)));
      }
      else if (myBinding instanceof BeanBinding && myValueAttribute.isEmpty()) {
        ((BeanBinding)myBinding).serializeInto(value, targetElement, filter);
      }
      else {
        Object node = myBinding.serialize(value, targetElement, filter);
        if (node != null && targetElement != node) {
          addContent(targetElement, node);
        }
      }
    }
    else {
      String text = converter.toString(value);
      if (text != null) {
        targetElement.setAttribute(myValueAttribute, XmlSerializerImpl.removeControlChars(text));
      }
    }
    return targetElement;
  }

  @Override
  @NotNull
  public Object deserialize(@NotNull Object context, @NotNull Element element) {
    Attribute valueAttribute = element.getAttribute(myValueAttribute);
    if (valueAttribute == null) {
      if (myValueAttribute.isEmpty()) {
        assert myBinding != null;
        myAccessor.set(context, myBinding.deserializeUnsafe(context, element));
      }
      else {
        List<Element> children = element.getChildren();
        if (children.isEmpty()) {
          myAccessor.set(context, null);
        }
        else {
          assert myBinding != null;
          myAccessor.set(context, Binding.deserializeList(myBinding, myAccessor.read(context), children));
        }
      }
    }
    else {
      String value = valueAttribute.getValue();
      if (myConverter == null) {
        try {
          XmlSerializerImpl
            .doSet(context, value, myAccessor, XmlSerializerImpl.typeToClass(myAccessor.getGenericType()));
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot set value for field " + myName, e);
        }
      }
      else {
        myAccessor.set(context, myConverter.fromString(value));
      }
    }
    return context;
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    if (!element.getName().equals(myTagName)) {
      return false;
    }

    String name = element.getAttributeValue(myNameAttribute);
    if (StringUtil.isEmpty(myNameAttribute)) {
      return name == null || name.equals(myName);
    }
    else {
      return name != null && name.equals(myName);
    }
  }

  @NonNls
  public String toString() {
    return "OptionTagBinding[" + myName + ", binding=" + myBinding + "]";
  }
}
