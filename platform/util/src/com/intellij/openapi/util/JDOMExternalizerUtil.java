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
package com.intellij.openapi.util;

import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMExternalizerUtil {

  private static final String VALUE_ATTR_NAME = "value";

  public static void writeField(@NotNull Element root, @NotNull @NonNls String fieldName, String value) {
    Element element = new Element("option");
    element.setAttribute("name", fieldName);
    element.setAttribute("value", value == null ? "" : value);
    root.addContent(element);
  }

  @NotNull
  public static String readField(@NotNull Element parent, @NotNull @NonNls String fieldName, @NotNull String defaultValue) {
    String val = readField(parent, fieldName);
    return val == null ? defaultValue : val;
  }

  @Nullable
  public static String readField(@NotNull Element parent, @NotNull @NonNls String fieldName) {
    for (Element element : JDOMUtil.getChildren(parent, "option")) {
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, fieldName)) {
        return element.getAttributeValue("value");
      }
    }
    return null;
  }

  public static Element getOption(@NotNull Element parent, @NotNull @NonNls String fieldName) {
    for (Element element : JDOMUtil.getChildren(parent, "option")) {
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, fieldName)) {
        return element;
      }
    }
    return null;
  }

  @NotNull
  public static Element writeOption(@NotNull Element root, @NotNull @NonNls String fieldName) {
    Element element = new Element("option");
    element.setAttribute("name", fieldName);
    root.addContent(element);
    return element;
  }

  @NotNull
  public static Element addElementWithValueAttribute(@NotNull Element parent, @NotNull String childTagName, @Nullable String attrValue) {
    Element element = new Element(childTagName);
    if (attrValue != null) {
      element.setAttribute(VALUE_ATTR_NAME, attrValue);
    }
    parent.addContent(element);
    return element;
  }

  @Nullable
  public static String getFirstChildValueAttribute(@NotNull Element parent, @NotNull String childTagName) {
    Element first = parent.getChild(childTagName);
    if (first != null) {
      return first.getAttributeValue(VALUE_ATTR_NAME);
    }
    return null;
  }

  @NotNull
  public static List<String> getChildrenValueAttributes(@NotNull Element parent, @NotNull String childTagName) {
    List<Element> children = parent.getChildren(childTagName);
    if (children.isEmpty()) {
      return Collections.emptyList();
    }
    if (children.size() == 1) {
      String value = children.iterator().next().getAttributeValue(VALUE_ATTR_NAME);
      return value == null ? Collections.<String>emptyList() : Collections.singletonList(value);
    }
    List<String> values = ContainerUtil.newArrayListWithCapacity(children.size());
    for (Element child : children) {
      String value = child.getAttributeValue(VALUE_ATTR_NAME);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }

  @SuppressWarnings("Duplicates")
  public static void addChildrenWithValueAttribute(@NotNull Element parent,
                                                   @NotNull String childTagName,
                                                   @NotNull List<String> attrValues) {
    for (String value : attrValues) {
      if (value != null) {
        Element child = new Element(childTagName);
        child.setAttribute(VALUE_ATTR_NAME, value);
        parent.addContent(child);
      }
    }
  }

  @SuppressWarnings({"deprecation", "Duplicates"})
  public static void addChildren(@NotNull Element parent,
                                 @NotNull String childElementName,
                                 @NotNull Collection<? extends JDOMExternalizable> children) throws WriteExternalException {
    for (JDOMExternalizable child : children) {
      if (child != null) {
        Element element = new Element(childElementName);
        child.writeExternal(element);
        parent.addContent(element);
      }
    }
  }
}
