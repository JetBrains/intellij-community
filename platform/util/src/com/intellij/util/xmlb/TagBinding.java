/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

class TagBinding extends BasePrimitiveBinding {
  private final String myTextIfEmpty;

  public TagBinding(@NotNull Accessor accessor, @NotNull Tag tagAnnotation) {
    super(accessor, tagAnnotation.value(), null);

    myTextIfEmpty = StringUtil.nullize(tagAnnotation.textIfEmpty());
  }

  @Nullable
  @Override
  public Object serialize(Object o, @Nullable Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    Element v = new Element(myName);
    if (value == null) {
      return v;
    }

    assert myBinding != null;
    Object node = myBinding.serialize(value, v, filter);
    if (node != null && node != v) {
      JDOMUtil.addContent(v, node);
    }
    return v;
  }

  @Override
  @Nullable
  public Object deserialize(Object o, @NotNull Object... nodes) {
    assert nodes.length > 0;
    List<? extends Content> children;
    boolean isBeanBinding = myBinding instanceof BeanBinding;
    if (nodes.length == 1) {
      Element node = (Element)nodes[0];
      children = isBeanBinding ? node.getChildren() : node.getContent();
    }
    else {
      String name = ((Element)nodes[0]).getName();
      children = new SmartList<Content>();
      for (Object node : nodes) {
        Element element = (Element)node;
        assert element.getName().equals(name);
        //noinspection unchecked
        children.addAll(((List)(isBeanBinding ? element.getChildren() : element.getContent())));
      }
    }

    assert myBinding != null;
    if (isBeanBinding && myAccessor.isFinal()) {
      ((BeanBinding)myBinding).deserializeInto(o, (Element)children.get(0), null);
    }
    else {
      if (children.isEmpty() && myTextIfEmpty != null) {
        children = Collections.<Content>singletonList(new Text(myTextIfEmpty));
      }

      Object v = myBinding.deserialize(myAccessor.read(o), ArrayUtil.toObjectArray(children));
      myAccessor.write(o, XmlSerializerImpl.convert(v, myAccessor.getValueClass()));
    }
    return o;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myName);
  }
}
