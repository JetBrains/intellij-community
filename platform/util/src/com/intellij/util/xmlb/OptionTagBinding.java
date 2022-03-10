// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class OptionTagBinding extends BasePrimitiveBinding {
  private final String myTagName;
  private final String myNameAttribute;
  private final String myValueAttribute;

  OptionTagBinding(@NotNull MutableAccessor accessor, @Nullable OptionTag optionTag) {
    super(accessor, optionTag == null ? null : optionTag.value(), optionTag == null ? null : optionTag.converter());

    if (optionTag == null) {
      myTagName = Constants.OPTION;
      myNameAttribute = Constants.NAME;
      myValueAttribute = Constants.VALUE;
    }
    else {
      myNameAttribute = StringUtil.internEmptyString(optionTag.nameAttribute());
      myValueAttribute = StringUtil.internEmptyString(optionTag.valueAttribute());

      String tagName = optionTag.tag();
      if (StringUtil.isEmpty(myNameAttribute) && Constants.OPTION.equals(tagName)) {
        tagName = myAccessor.getName();
      }
      myTagName = tagName;
    }
  }

  @Override
  public @NotNull Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    Element targetElement = new Element(myTagName);

    if (!StringUtil.isEmpty(myNameAttribute)) {
      targetElement.setAttribute(myNameAttribute, name);
    }

    if (value == null) {
      return targetElement;
    }

    Converter<Object> converter = getConverter();
    if (converter == null) {
      if (myBinding == null) {
        targetElement.setAttribute(myValueAttribute, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)));
      }
      else if (myBinding instanceof BeanBinding && myValueAttribute.isEmpty()) {
        ((BeanBinding)myBinding).serializeInto(value, targetElement, filter);
      }
      else {
        Object node = myBinding.serialize(value, targetElement, filter);
        if (node != null && targetElement != node) {
          Binding.addContent(targetElement, node);
        }
      }
    }
    else {
      String text = converter.toString(value);
      if (text != null) {
        targetElement.setAttribute(myValueAttribute, JDOMUtil.removeControlChars(text));
      }
    }
    return targetElement;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull Element element) {
    Attribute valueAttribute = element.getAttribute(myValueAttribute);
    if (valueAttribute == null) {
      if (myValueAttribute.isEmpty()) {
        assert myBinding != null;
        myAccessor.set(context, myBinding.deserializeUnsafe(context, element));
      }
      else {
        List<Element> children = element.getChildren();
        if (children.isEmpty()) {
          if (myBinding instanceof CollectionBinding || myBinding instanceof MapBinding) {
            Object oldValue = myAccessor.read(context);
            // do nothing if field is already null
            if (oldValue != null) {
              Object newValue = ((MultiNodeBinding)myBinding).deserializeList(oldValue, children);
              if (oldValue != newValue) {
                myAccessor.set(context, newValue);
              }
            }
          }
          else {
            myAccessor.set(context, null);
          }
        }
        else {
          assert myBinding != null;
          Object oldValue = myAccessor.read(context);
          Object newValue = Binding.deserializeList(myBinding, oldValue, children);
          if (oldValue != newValue) {
            myAccessor.set(context, newValue);
          }
        }
      }
    }
    else {
      String value = valueAttribute.getValue();
      if (myConverter == null) {
        try {
          XmlSerializerImpl.doSet(context, value, myAccessor, ClassUtil.typeToClass(myAccessor.getGenericType()));
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot set value for field " + name, e);
        }
      }
      else {
        myAccessor.set(context, myConverter.fromString(value));
      }
    }
    return context;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull XmlElement element) {
    String value = element.getAttributeValue(myValueAttribute);
    if (value == null) {
      if (myValueAttribute.isEmpty()) {
        assert myBinding != null;
        myAccessor.set(context, myBinding.deserializeUnsafe(context, element));
      }
      else {
        List<XmlElement> children = element.children;
        if (children.isEmpty()) {
          if (myBinding instanceof CollectionBinding || myBinding instanceof MapBinding) {
            Object oldValue = myAccessor.read(context);
            // do nothing if field is already null
            if (oldValue != null) {
              Object newValue = ((MultiNodeBinding)myBinding).deserializeList2(oldValue, children);
              if (oldValue != newValue) {
                myAccessor.set(context, newValue);
              }
            }
          }
          else {
            myAccessor.set(context, null);
          }
        }
        else {
          assert myBinding != null;
          Object oldValue = myAccessor.read(context);
          Object newValue = Binding.deserializeList2(myBinding, oldValue, children);
          if (oldValue != newValue) {
            myAccessor.set(context, newValue);
          }
        }
      }
    }
    else {
      if (myConverter == null) {
        try {
          XmlSerializerImpl.doSet(context, value, myAccessor, ClassUtil.typeToClass(myAccessor.getGenericType()));
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot set value for field " + name, e);
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
      return name == null || name.equals(this.name);
    }
    else {
      return name != null && name.equals(this.name);
    }
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    if (!element.name.equals(myTagName)) {
      return false;
    }

    String name = element.getAttributeValue(myNameAttribute);
    if (StringUtil.isEmpty(myNameAttribute)) {
      return name == null || name.equals(this.name);
    }
    else {
      return name != null && name.equals(this.name);
    }
  }

  @NonNls
  public String toString() {
    return "OptionTagBinding[" + name + ", binding=" + myBinding + "]";
  }
}
