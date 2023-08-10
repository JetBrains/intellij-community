// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class TagBinding extends BasePrimitiveBinding implements MultiNodeBinding {
  private final String myTextIfEmpty;

  TagBinding(@NotNull MutableAccessor accessor, @NotNull Tag tagAnnotation) {
    super(accessor, tagAnnotation.value(), null);

    myTextIfEmpty = tagAnnotation.textIfEmpty();
  }

  TagBinding(@NotNull MutableAccessor accessor, @NotNull String suggestedName) {
    super(accessor, suggestedName, null);

    myTextIfEmpty = "";
  }

  @Override
  public @Nullable Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
    Object value = accessor.read(o);
    if (value == null) {
      return null;
    }

    Element serialized = new Element(name);
    if (binding == null) {
      serialized.addContent(new Text(XmlSerializerImpl.convertToString(value)));
    }
    else {
      Object node = binding.serialize(value, serialized, filter);
      if (node != null && node != serialized) {
        Binding.addContent(serialized, node);
      }
    }
    return serialized;
  }

  @Override
  public @NotNull Object deserializeList(@NotNull Object context, @NotNull List<? extends Element> elements) {
    List<Element> children;
    if (elements.size() == 1) {
      children = elements.get(0).getChildren();
    }
    else {
      String name = elements.get(0).getName();
      children = new ArrayList<>();
      for (Element element : elements) {
        assert element.getName().equals(name);
        children.addAll(element.getChildren());
      }
    }
    deserialize(context, children);
    return context;
  }

  @Override
  public @NotNull Object deserializeList2(@NotNull Object context, @NotNull List<XmlElement> elements) {
    List<XmlElement> children;
    if (elements.size() == 1) {
      children = elements.get(0).children;
    }
    else {
      String name = elements.get(0).name;
      children = new ArrayList<>();
      for (XmlElement element : elements) {
        assert element.name.equals(name);
        children.addAll(element.children);
      }
    }
    deserialize2(context, children);
    return context;
  }

  @Override
  public boolean isMulti() {
    return binding instanceof MultiNodeBinding && ((MultiNodeBinding)binding).isMulti();
  }

  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull Element element) {
    if (binding == null) {
      String value = XmlSerializerImpl.getTextValue(element, myTextIfEmpty);
      XmlSerializerImpl.doSet(context, value, accessor, ClassUtil.typeToClass(accessor.getGenericType()));
    }
    else {
      deserialize(context, element.getChildren());
    }
    return context;
  }

  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull XmlElement element) {
    if (binding == null) {
      String value = element.content;
      if (value == null) {
        value = myTextIfEmpty;
      }
      XmlSerializerImpl.doSet(context, value, accessor, ClassUtil.typeToClass(accessor.getGenericType()));
    }
    else {
      deserialize2(context, element.children);
    }
    return context;
  }

  private void deserialize(@NotNull Object context, @NotNull List<? extends Element> children) {
    assert binding != null;
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(context, children.get(0));
    }
    else if ((binding instanceof CollectionBinding || binding instanceof MapBinding) && !accessor.isWritable()) {
      Binding.deserializeList(binding, accessor.read(context), children);
    }
    else {
      accessor.set(context, Binding.deserializeList(binding, accessor.read(context), children));
    }
  }

  private void deserialize2(@NotNull Object context, @NotNull List<XmlElement> children) {
    assert binding != null;
    if (binding instanceof BeanBinding && !accessor.isWritable()) {
      ((BeanBinding)binding).deserializeInto(context, children.get(0));
    }
    else if ((binding instanceof CollectionBinding || binding instanceof MapBinding) && !accessor.isWritable()) {
      Binding.deserializeList2(binding, accessor.read(context), children);
    }
    else {
      accessor.set(context, Binding.deserializeList2(binding, accessor.read(context), children));
    }
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return element.getName().equals(name);
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    return element.name.equals(name);
  }
}