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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class TagBinding extends BasePrimitiveBinding implements MultiNodeBinding {
  private final String myTextIfEmpty;

  public TagBinding(@NotNull Accessor accessor, @NotNull Tag tagAnnotation) {
    super(accessor, tagAnnotation.value(), null);

    myTextIfEmpty = StringUtil.nullize(tagAnnotation.textIfEmpty());
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
      serialized.addContent(new Text(TextBinding.convertToString(value)));
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
  public Object deserializeList(Object context, @NotNull List<?> nodes) {
    boolean isBeanBinding = myBinding instanceof BeanBinding;
    List<? extends Content> children;
    if (nodes.size() == 1) {
      children = getContents(isBeanBinding, (Element)nodes.get(0));
    }
    else {
      String name = ((Element)nodes.get(0)).getName();
      children = new SmartList<Content>();
      for (Object node : nodes) {
        Element element = (Element)node;
        assert element.getName().equals(name);
        //noinspection unchecked
        children.addAll(((List)getContents(isBeanBinding, element)));
      }
    }
    return deserialize(context, children, isBeanBinding);
  }

  @Override
  public boolean isMulti() {
    return myBinding instanceof MultiNodeBinding && ((MultiNodeBinding)myBinding).isMulti();
  }

  @Override
  @Nullable
  public Object deserialize(Object context, @NotNull Object node) {
    boolean isBeanBinding = myBinding instanceof BeanBinding;
    Element element = (Element)node;
    return deserialize(context, getContents(isBeanBinding, element), isBeanBinding);
  }

  private Object deserialize(Object o, List<? extends Content> children, boolean isBeanBinding) {
    if (isBeanBinding && myAccessor.isFinal()) {
      assert myBinding != null;
      ((BeanBinding)myBinding).deserializeInto(o, (Element)children.get(0), null);
    }
    else if (myTextIfEmpty != null && children.isEmpty()) {
      XmlSerializerImpl.doSet(o, myTextIfEmpty, myAccessor, XmlSerializerImpl.typeToClass(myAccessor.getGenericType()));
    }
    else if (myBinding == null) {
      XmlSerializerImpl.doSet(o, children.isEmpty() ? "" : children.get(0).getValue(), myAccessor, XmlSerializerImpl.typeToClass(myAccessor.getGenericType()));
    }
    else {
      myAccessor.set(o, Binding.deserializeList(myBinding, myAccessor.read(o), children));
    }
    return o;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myName);
  }

  @NotNull
  private static List<? extends Content> getContents(boolean isBeanBinding, Element element) {
    return isBeanBinding ? element.getChildren() : XmlSerializerImpl.getFilteredContent(element);
  }
}
