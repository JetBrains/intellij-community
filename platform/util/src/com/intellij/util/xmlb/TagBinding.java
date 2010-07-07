/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jdom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TagBinding implements Binding {
  private final Accessor accessor;
  private final Tag myTagAnnotation;
  private final String myTagName;
  private final Binding binding;

  public TagBinding(Accessor accessor, Tag tagAnnotation, XmlSerializerImpl xmlSerializer) {
    this.accessor = accessor;
    myTagAnnotation = tagAnnotation;
    myTagName = tagAnnotation.value();
    binding = xmlSerializer.getBinding(accessor);
  }

  public Object serialize(Object o, Object context) {
    Object value = accessor.read(o);
    if (value == null) return context;

    Element v = new Element(myTagName);

    Object node = binding.serialize(value, v);
    if (node != v) {
      JDOMUtil.addContent(v, node);
    }

    return v;
  }

  public Object deserialize(Object o, Object... nodes) {
    assert nodes.length > 0;
    Object[] children;
    if (nodes.length == 1) {
      children = JDOMUtil.getContent((Element)nodes[0]);
    }
    else {
      String name = ((Element)nodes[0]).getName();
      List<Object> childrenList = new ArrayList<Object>();
      for (Object node : nodes) {
        assert ((Element)node).getName().equals(name);
        ContainerUtil.addAll(childrenList, JDOMUtil.getContent((Element)node));
      }

      children = ArrayUtil.toObjectArray(childrenList);
    }

    if (children.length == 0) {
      children = new Object[] {new Text(myTagAnnotation.textIfEmpty())};
    }

    Object v = binding.deserialize(accessor.read(o), children);
    Object value = XmlSerializerImpl.convert(v, accessor.getValueClass());
    accessor.write(o, value);
    return o;
  }

  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  public Class getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  public void init() {
  }
}
