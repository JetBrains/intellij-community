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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class OptionTagBinding implements Binding {
  private final static Logger LOG = Logger.getInstance("#" + OptionTagBinding.class.getName());

  private final Accessor accessor;
  private final String myName;
  private final Binding myBinding;
  private final String myTagName;
  private final String myNameAttribute;
  private final String myValueAttribute;

  public OptionTagBinding(Accessor accessor, @Nullable OptionTag optionTag) {
    this.accessor = accessor;
    myBinding = XmlSerializerImpl.getBinding(accessor);
    if (optionTag != null) {
      String name = optionTag.value();
      myName = name.isEmpty() ? accessor.getName() : name;
      myTagName = optionTag.tag();
      myNameAttribute = optionTag.nameAttribute();
      myValueAttribute = optionTag.valueAttribute();
    }
    else {
      myName = accessor.getName();
      myTagName = Constants.OPTION;
      myNameAttribute = Constants.NAME;
      myValueAttribute = Constants.VALUE;
    }
  }

  @Override
  public Object serialize(Object o, Object context, SerializationFilter filter) {
    Element targetElement = new Element(myTagName);
    Object value = accessor.read(o);

    if (!StringUtil.isEmpty(myNameAttribute)) {
      targetElement.setAttribute(myNameAttribute, myName);
    }

    if (value == null) return targetElement;

    Object node = myBinding.serialize(value, targetElement, filter);
    if (node instanceof Text) {
      Text text = (Text)node;
      targetElement.setAttribute(myValueAttribute, text.getText());
    }
    else {
      if (targetElement != node) {
        JDOMUtil.addContent(targetElement, node);
      }
    }

    return targetElement;
  }

  @Override
  public Object deserialize(Object o, @NotNull Object... nodes) {
    if (nodes.length > 1) {
      LOG.info("Duplicate options for " + o + " will be ignored");
    }
    assert nodes.length != 0 : "Empty nodes passed to: " + this;

    Element element = ((Element)nodes[0]);
    Attribute valueAttr = element.getAttribute(myValueAttribute);

    if (valueAttr != null) {
      Object value = myBinding.deserialize(o, valueAttr);
      accessor.write(o, value);
    }
    else {
      final Content[] childElements = JDOMUtil.getContent(element);
      List<Object> children = new ArrayList<Object>();

      for (final Content child : childElements) {
        if (XmlSerializerImpl.isIgnoredNode(child)) continue;
        children.add(child);
      }

      if (children.size() > 0) {
        Object value = myBinding.deserialize(accessor.read(o), ArrayUtil.toObjectArray(children));
        accessor.write(o, value);
      }
      else {
        accessor.write(o, null);
      }
    }

    return o;
  }

  @Override
  public boolean isBoundTo(Object node) {
    if (!(node instanceof Element)) return false;
    Element e = (Element)node;
    if (!e.getName().equals(myTagName)) return false;
    String name = e.getAttributeValue(myNameAttribute);
    if (StringUtil.isEmpty(myNameAttribute)) {
      return name == null || name.equals(myName);
    }
    return name != null && name.equals(myName);
  }

  @Override
  public Class getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  @Override
  public void init() {
  }


  @NonNls
  public String toString() {
    return "OptionTagBinding[" + myName + ", binding=" + myBinding + "]";
  }
}
