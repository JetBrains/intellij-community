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
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class OptionTagBinding extends BasePrimitiveBinding {
  private final String myTagName;
  private final String myNameAttribute;
  private final String myValueAttribute;

  public OptionTagBinding(@NotNull Accessor accessor, @Nullable OptionTag optionTag) {
    super(accessor, optionTag == null ? null : optionTag.value(), optionTag == null ? null : optionTag.converter());

    if (optionTag == null) {
      myTagName = Constants.OPTION;
      myNameAttribute = Constants.NAME;
      myValueAttribute = Constants.VALUE;
    }
    else {
      myNameAttribute = optionTag.nameAttribute();
      myValueAttribute = optionTag.valueAttribute();

      String tagName = optionTag.tag();
      if (StringUtil.isEmpty(myNameAttribute) && Constants.OPTION.equals(tagName)) {
        tagName = myAccessor.getName();
      }
      myTagName = tagName;
    }
  }

  @Override
  @Nullable
  public Object serialize(Object o, @Nullable Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    Element targetElement = new Element(myTagName);

    if (!StringUtil.isEmpty(myNameAttribute)) {
      targetElement.setAttribute(myNameAttribute, myName);
    }

    if (value == null) {
      return targetElement;
    }

    if (myConverter != null) {
      targetElement.setAttribute(myValueAttribute, myConverter.toString(value));
    }
    else {
      assert myBinding != null;
      Object node = myBinding.serialize(value, targetElement, filter);
      if (node != null) {
        if (node instanceof Text) {
          Text text = (Text)node;
          targetElement.setAttribute(myValueAttribute, text.getText());
        }
        else if (targetElement != node) {
          JDOMUtil.addContent(targetElement, node);
        }
      }
    }
    return targetElement;
  }

  @Override
  public Object deserialize(Object context, @NotNull Object node) {
    return deserialize(context, (Element)node);
  }

  private Object deserialize(Object context, Element element) {
    Attribute valueAttribute = element.getAttribute(myValueAttribute);
    if (valueAttribute == null) {
      List<Content> children = XmlSerializerImpl.getFilteredContent(element);
      if (children.isEmpty()) {
        myAccessor.write(context, null);
      }
      else {
        assert myBinding != null;
        myAccessor.write(context, Binding.deserializeList(myBinding, myAccessor.read(context), children));
      }
    }
    else {
      Object value;
      if (myConverter != null) {
        value = myConverter.fromString(valueAttribute.getValue());
      }
      else {
        assert myBinding != null;
        value = myBinding.deserialize(context, valueAttribute);
      }
      myAccessor.write(context, value);
    }
    return context;
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

  @NonNls
  public String toString() {
    return "OptionTagBinding[" + myName + ", binding=" + myBinding + "]";
  }
}
