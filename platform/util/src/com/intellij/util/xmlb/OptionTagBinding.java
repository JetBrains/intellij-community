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
import com.intellij.util.ArrayUtil;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

//todo: use TagBinding
class OptionTagBinding implements Binding {

  private final static Logger LOG = Logger.getInstance("#" + OptionTagBinding.class.getName());

  private final Accessor accessor;
  private final String myName;
  private final Binding myBinding;

  public OptionTagBinding(Accessor accessor, XmlSerializerImpl xmlSerializer) {
    this.accessor = accessor;
    myName = accessor.getName();
    myBinding = xmlSerializer.getBinding(accessor);
  }

  public Object serialize(Object o, Object context) {
    Element targetElement = new Element(Constants.OPTION);
    Object value = accessor.read(o);

    targetElement.setAttribute(Constants.NAME, myName);

    if (value == null) return targetElement;

    Object node = myBinding.serialize(value, targetElement);
    if (node instanceof Text) {
      Text text = (Text)node;
      targetElement.setAttribute(Constants.VALUE, text.getText());
    }
    else {
      if (targetElement != node) {
        JDOMUtil.addContent(targetElement, node);
      }
    }

    return targetElement;
  }

  public Object deserialize(Object o, Object... nodes) {
    if (nodes.length > 1) {
      LOG.info("Duplicated options for " + o + " will be ignored");
    }
    assert nodes.length != 0 : "Empty nodes passed to: " + this;

    Element element = ((Element)nodes[0]);
    Attribute valueAttr = element.getAttribute(Constants.VALUE);

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

  public boolean isBoundTo(Object node) {
    if (!(node instanceof Element)) return false;
    Element e = (Element)node;
    if (!e.getName().equals(Constants.OPTION)) return false;
    String name = e.getAttributeValue(Constants.NAME);
    return name != null && name.equals(myName);
  }

  public Class getBoundNodeType() {
    throw new UnsupportedOperationException("Method getBoundNodeType is not supported in " + getClass());
  }

  public void init() {
  }


  @NonNls
  public String toString() {
    return "OptionTagBinding[" + myName + ", binding=" + myBinding + "]";
  }
}
