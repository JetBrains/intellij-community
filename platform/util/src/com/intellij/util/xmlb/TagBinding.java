/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class TagBinding extends BasePrimitiveBinding implements MultiNodeBinding {
  private final String myTextIfEmpty;

  public TagBinding(@NotNull MutableAccessor accessor, @NotNull Tag tagAnnotation) {
    super(accessor, tagAnnotation.value(), null);

    myTextIfEmpty = tagAnnotation.textIfEmpty();
  }

  public TagBinding(@NotNull MutableAccessor accessor, @NotNull String suggestedName) {
    super(accessor, suggestedName, null);

    myTextIfEmpty = "";
  }

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable SerializationFilter filter) {
    Object value = myAccessor.read(o);
    Element serialized = new Element(myName);
    if (value == null) {
      return serialized;
    }

    if (myBinding == null) {
      serialized.addContent(new Text(XmlSerializerImpl.convertToString(value)));
    }
    else {
      Object node = myBinding.serialize(value, serialized, filter);
      if (node != null && node != serialized) {
        addContent(serialized, node);
      }
    }
    return serialized;
  }

  @Nullable
  @Override
  public Object deserializeList(@NotNull Object context, @NotNull List<Element> elements) {
    List<Element> children;
    if (elements.size() == 1) {
      children = elements.get(0).getChildren();
    }
    else {
      String name = elements.get(0).getName();
      children = new ArrayList<Element>();
      for (Element element : elements) {
        assert element.getName().equals(name);
        //noinspection unchecked
        children.addAll(element.getChildren());
      }
    }
    deserialize(context, children);
    return context;
  }

  @Override
  public boolean isMulti() {
    return myBinding instanceof MultiNodeBinding && ((MultiNodeBinding)myBinding).isMulti();
  }

  @Override
  @NotNull
  public Object deserialize(@NotNull Object context, @NotNull Element element) {
    if (myBinding == null) {
      String value = XmlSerializerImpl.getTextValue(element, myTextIfEmpty);
      XmlSerializerImpl.doSet(context, value, myAccessor, XmlSerializerImpl.typeToClass(myAccessor.getGenericType()));
    }
    else {
      deserialize(context, element.getChildren());
    }
    return context;
  }

  private void deserialize(@NotNull Object context, @NotNull List<Element> children) {
    assert myBinding != null;
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(context, children.get(0));
    }
    else {
      myAccessor.set(context, Binding.deserializeList(myBinding, myAccessor.read(context), children));
    }
  }

  @Override
  public boolean isBoundTo(@NotNull Element node) {
    return node.getName().equals(myName);
  }
}