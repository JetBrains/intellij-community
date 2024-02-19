// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class OptionTagBinding extends BasePrimitiveBinding {
  private final String tagName;
  private final String nameAttribute;
  private final String valueAttribute;

  OptionTagBinding(@NotNull MutableAccessor accessor, @Nullable OptionTag optionTag) {
    super(accessor, optionTag == null ? null : optionTag.value(), optionTag == null ? null : optionTag.converter());

    if (optionTag == null) {
      tagName = Constants.OPTION;
      nameAttribute = Constants.NAME;
      valueAttribute = Constants.VALUE;
    }
    else {
      nameAttribute = optionTag.nameAttribute();
      valueAttribute = optionTag.valueAttribute();

      String tagName = optionTag.tag();
      if ((nameAttribute == null || nameAttribute.isEmpty()) && Constants.OPTION.equals(tagName)) {
        tagName = this.accessor.getName();
      }
      this.tagName = tagName;
    }
  }

  @Override
  public void setValue(@NotNull Object host, @NotNull String value) {
    if (converter == null) {
      try {
        XmlSerializerImpl.doSet(host, value, accessor, ClassUtil.typeToClass(accessor.getGenericType()));
      }
      catch (Exception e) {
        throw new RuntimeException("Cannot set value for field " + name, e);
      }
    }
    else {
      accessor.set(host, converter.fromString(value));
    }
  }

  @Override
  public @NotNull Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
    Object value = accessor.read(o);
    Element targetElement = new Element(tagName);

    if (nameAttribute != null && !nameAttribute.isEmpty()) {
      targetElement.setAttribute(nameAttribute, name);
    }

    if (value == null) {
      return targetElement;
    }

    Converter<Object> converter = getConverter();
    if (converter == null) {
      if (binding == null) {
        targetElement.setAttribute(valueAttribute, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)));
      }
      else if (binding instanceof BeanBinding && valueAttribute.isEmpty()) {
        ((BeanBinding)binding).serializeInto(value, targetElement, filter);
      }
      else {
        Object node = binding.serialize(value, targetElement, filter);
        if (node != null && targetElement != node) {
          addContent(targetElement, node);
        }
      }
    }
    else {
      String text = converter.toString(value);
      if (text != null) {
        targetElement.setAttribute(valueAttribute, JDOMUtil.removeControlChars(text));
      }
    }
    return targetElement;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull Element element) {
    Attribute valueAttribute = element.getAttribute(this.valueAttribute);
    if (valueAttribute == null) {
      if (this.valueAttribute.isEmpty()) {
        assert binding != null;
        accessor.set(context, binding.deserializeUnsafe(context, element));
      }
      else {
        List<Element> children = element.getChildren();
        if (children.isEmpty()) {
          if (binding instanceof CollectionBinding || binding instanceof MapBinding) {
            Object oldValue = accessor.read(context);
            // do nothing if the field is already null
            if (oldValue != null) {
              Object newValue = ((MultiNodeBinding)binding).deserializeJdomList(oldValue, children);
              if (oldValue != newValue) {
                accessor.set(context, newValue);
              }
            }
          }
          else {
            accessor.set(context, null);
          }
        }
        else {
          assert binding != null;
          Object oldValue = accessor.read(context);
          Object newValue = Binding.deserializeJdomList(binding, oldValue, children);
          if (oldValue != newValue) {
            accessor.set(context, newValue);
          }
        }
      }
    }
    else {
      setValue(context, valueAttribute.getValue());
    }
    return context;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull XmlElement element) {
    String value = element.getAttributeValue(valueAttribute);
    if (value == null) {
      if (valueAttribute.isEmpty()) {
        assert binding != null;
        accessor.set(context, binding.deserializeUnsafe(context, element));
      }
      else {
        List<XmlElement> children = element.children;
        if (children.isEmpty()) {
          if (binding instanceof CollectionBinding || binding instanceof MapBinding) {
            Object oldValue = accessor.read(context);
            // do nothing if the field is already null
            if (oldValue != null) {
              Object newValue = ((MultiNodeBinding)binding).deserializeList(oldValue, children);
              if (oldValue != newValue) {
                accessor.set(context, newValue);
              }
            }
          }
          else {
            accessor.set(context, null);
          }
        }
        else {
          assert binding != null;
          Object oldValue = accessor.read(context);
          Object newValue = Binding.deserializeList(binding, oldValue, children);
          if (oldValue != newValue) {
            accessor.set(context, newValue);
          }
        }
      }
    }
    else {
      if (converter == null) {
        try {
          XmlSerializerImpl.doSet(context, value, accessor, ClassUtil.typeToClass(accessor.getGenericType()));
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot set value for field " + name, e);
        }
      }
      else {
        accessor.set(context, converter.fromString(value));
      }
    }
    return context;
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    if (!element.getName().equals(tagName)) {
      return false;
    }

    String name = element.getAttributeValue(nameAttribute);
    if (nameAttribute == null || nameAttribute.isEmpty()) {
      return name == null || name.equals(this.name);
    }
    else {
      return name != null && name.equals(this.name);
    }
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    if (!element.name.equals(tagName)) {
      return false;
    }

    String name = element.getAttributeValue(nameAttribute);
    if (nameAttribute.isEmpty()) {
      return name == null || name.equals(this.name);
    }
    else {
      return name != null && name.equals(this.name);
    }
  }

  public @NonNls String toString() {
    return "OptionTagBinding[" + name + ", binding=" + binding + "]";
  }
}
