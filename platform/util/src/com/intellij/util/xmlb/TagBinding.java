// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
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
    Object value = myAccessor.read(o);
    if (value == null) {
      return null;
    }

    Element serialized = new Element(myName);
    if (myBinding == null) {
      serialized.addContent(new Text(XmlSerializerImpl.convertToString(value)));
    }
    else {
      Object node = myBinding.serialize(value, serialized, filter);
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
  public boolean isMulti() {
    return myBinding instanceof MultiNodeBinding && ((MultiNodeBinding)myBinding).isMulti();
  }

  @Override
  public @NotNull Object deserialize(@NotNull Object context, @NotNull Element element) {
    if (myBinding == null) {
      String value = XmlSerializerImpl.getTextValue(element, myTextIfEmpty);
      XmlSerializerImpl.doSet(context, value, myAccessor, ClassUtil.typeToClass(myAccessor.getGenericType()));
    }
    else {
      deserialize(context, element.getChildren());
    }
    return context;
  }

  private void deserialize(@NotNull Object context, @NotNull List<? extends Element> children) {
    assert myBinding != null;
    if (myBinding instanceof BeanBinding && !myAccessor.isWritable()) {
      ((BeanBinding)myBinding).deserializeInto(context, children.get(0));
    }
    else if ((myBinding instanceof CollectionBinding || myBinding instanceof MapBinding) && !myAccessor.isWritable()) {
      Binding.deserializeList(myBinding, myAccessor.read(context), children);
    }
    else {
      myAccessor.set(context, Binding.deserializeList(myBinding, myAccessor.read(context), children));
    }
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    return element.getName().equals(myName);
  }
}