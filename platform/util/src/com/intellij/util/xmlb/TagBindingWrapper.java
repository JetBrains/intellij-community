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
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;

//todo: merge with option tag binding
class TagBindingWrapper implements Binding {
  private final Binding binding;
  private final String myTagName;
  private final String myAttributeName;

  public TagBindingWrapper(Binding binding, final String tagName, final String attributeName) {
    this.binding = binding;

    assert binding.getBoundNodeType().isAssignableFrom(Text.class);
    myTagName = tagName;
    myAttributeName = attributeName;
  }

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Element e = new Element(myTagName);
    Object n = binding.serialize(o, e, filter);

    final String value = ((Content)n).getValue();

    if (myAttributeName.length() != 0) {
      e.setAttribute(myAttributeName, value);
    }
    else {
      e.addContent(new Text(value));
    }

    return e;
  }

  public Object deserialize(Object context, @NotNull Object... nodes) {
    assert nodes.length == 1;

    Element e = (Element)nodes[0];
    final Object[] childNodes;
    if (myAttributeName.length() != 0) {
      childNodes = new Object[]{e.getAttribute(myAttributeName)};
    }
    else {
      childNodes = JDOMUtil.getContent(e);
    }
  
    return binding.deserialize(context, childNodes);
  }

  public boolean isBoundTo(Object node) {
    return node instanceof Element && ((Element)node).getName().equals(myTagName);
  }

  public Class getBoundNodeType() {
    return Element.class;
  }

  public void init() {
  }
}
