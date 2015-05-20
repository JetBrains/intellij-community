/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
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

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @NotNull SerializationFilter filter) {
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
        JDOMUtil.addContent(serialized, node);
      }
    }
    return serialized;
  }

  @Nullable
  @Override
  public Object deserializeList(Object context, @NotNull List<Element> elements) {
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
        children.addAll(((List)element.getChildren()));
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
  @Nullable
  public Object deserialize(Object context, @NotNull Element element) {
    if (myBinding == null) {
      String value = XmlSerializerImpl.getTextValue(element, myTextIfEmpty);
      XmlSerializerImpl.doSet(context, value, myAccessor, XmlSerializerImpl.typeToClass(myAccessor.getGenericType()));
    }
    else {
      deserialize(context, element.getChildren());
    }
    return context;
  }

  private void deserialize(Object o, @NotNull List<Element> children) {
    assert myBinding != null;
    if (myBinding instanceof BeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(o, children.get(0), null);
    }
    else {
      myAccessor.set(o, Binding.deserializeList(myBinding, myAccessor.read(o), children));
    }
  }

  @Override
  public boolean isBoundTo(@NotNull Element node) {
    return node.getName().equals(myName);
  }
}