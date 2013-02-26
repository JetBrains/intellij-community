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

import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

class JDOMElementBinding implements Binding {
  private final Accessor myAccessor;
  private final String myTagName;

  public JDOMElementBinding(final Accessor accessor) {
    myAccessor = accessor;
    final Tag tag = XmlSerializerImpl.findAnnotation(myAccessor.getAnnotations(), Tag.class);
    assert tag != null : "jdom.Element property without @Tag annotation: " + accessor;
    myTagName = tag.value();
  }

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      return context;
    }
    if (value instanceof Element) {
      Element targetElement;
      targetElement = (Element)((Element)value).clone();
      targetElement.setName(myTagName);
      return targetElement;
    }
    if (value instanceof Element[]) {
      ArrayList<Element> result = new ArrayList<Element>();
      for (Element element : ((Element[])value)) {
        Element target = ((Element)element.clone()).setName(myTagName);
        result.add(target);

      }
      return result;
    }
    throw new XmlSerializationException("org.jdom.Element expected but " + value + " found");
  }

  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    Element[] result = new Element[nodes.length];

    System.arraycopy(nodes, 0, result, 0, nodes.length);

    if (myAccessor.getValueClass().isArray()) {
      myAccessor.write(context, result);
    }
    else {
      assert result.length == 1;
      myAccessor.write(context, result[0]);
    }
    return context;
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
