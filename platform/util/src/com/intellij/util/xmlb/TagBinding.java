/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class TagBinding extends BasePrimitiveBinding {
  private final Tag myTagAnnotation;

  public TagBinding(Accessor accessor, Tag tagAnnotation) {
    super(accessor, tagAnnotation.value(), null);

    myTagAnnotation = tagAnnotation;
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
    Object[] children;
    if (nodes.length == 1) {
      children = JDOMUtil.getContent((Element)nodes[0]);
    }
    else {
      String name = ((Element)nodes[0]).getName();
      List<Content> childrenList = new SmartList<Content>();
      for (Object node : nodes) {
        assert ((Element)node).getName().equals(name);
        childrenList.addAll(((Element)node).getContent());
      }
      children = ArrayUtil.toObjectArray(childrenList);
    }

    if (children.length == 0) {
      children = new Object[] {new Text(myTagAnnotation.textIfEmpty())};
    }

    assert myBinding != null;
    Object v = myBinding.deserialize(myAccessor.read(o), children);
    Object value = XmlSerializerImpl.convert(v, myAccessor.getValueClass());
    myAccessor.write(o, value);
    return o;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myName);
  }
}
