// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.openapi.util;

import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JDOMExternalizerUtil {
  private static final String OPTION_TAG = "option";
  private static final String NAME_ATTR = "name";
  private static final String VALUE_ATTR = "value";

  /**
   * Adds {@code <option name="{fieldName}" value="{value}"/>} element to the parent.
   */
  public static void writeField(@NotNull Element parent, @NotNull String fieldName, @Nullable String value) {
    Element element = new Element(OPTION_TAG);
    element.setAttribute(NAME_ATTR, fieldName);
    element.setAttribute(VALUE_ATTR, value == null ? "" : value);
    parent.addContent(element);
  }

  /**
   * Adds {@code <option name="{fieldName}" value="{value}"/>} element to the parent when the value differs from the default.
   */
  public static void writeField(@NotNull Element parent, @NotNull String fieldName, @Nullable String value, @NotNull String defaultValue) {
    if (!defaultValue.equals(value)) {
      writeField(parent, fieldName, value);
    }
  }

  @NotNull
  public static String readField(@NotNull Element parent, @NotNull String fieldName, @NotNull String defaultValue) {
    String val = readField(parent, fieldName);
    return val == null ? defaultValue : val;
  }

  @Nullable
  public static String readField(@NotNull Element parent, @NotNull String fieldName) {
    for (Element element : parent.getChildren(OPTION_TAG)) {
      if (fieldName.equals(element.getAttributeValue(NAME_ATTR))) {
        return element.getAttributeValue(VALUE_ATTR);
      }
    }
    return null;
  }

  /**
   * Adds {@code <option name="{fieldName}"/>} element to the parent and returns the created element.
   */
  @NotNull
  public static Element writeOption(@NotNull Element parent, @NotNull String fieldName) {
    Element element = new Element(OPTION_TAG);
    element.setAttribute(NAME_ATTR, fieldName);
    parent.addContent(element);
    return element;
  }

  @Nullable
  public static Element readOption(@NotNull Element parent, @NotNull String fieldName) {
    for (Element element : parent.getChildren(OPTION_TAG)) {
      if (fieldName.equals(element.getAttributeValue(NAME_ATTR))) {
        return element;
      }
    }
    return null;
  }

  /**
   * Adds {@code <{tagName} value="{value}"/>} element to the parent (or just {@code <{tagName}"/>} if the value is {@code null}).
   */
  public static void writeCustomField(@NotNull Element parent, @NotNull String tagName, @Nullable String value) {
    Element element = new Element(tagName);
    if (value != null) {
      element.setAttribute(VALUE_ATTR, value);
    }
    parent.addContent(element);
  }

  @Nullable
  public static String readCustomField(@NotNull Element parent, @NotNull String tagName) {
    Element element = parent.getChild(tagName);
    return element != null ? element.getAttributeValue(VALUE_ATTR) : null;
  }

  @NotNull
  public static List<String> getChildrenValueAttributes(@NotNull Element parent, @NotNull String childTagName) {
    List<Element> children = parent.getChildren(childTagName);
    if (children.isEmpty()) {
      return Collections.emptyList();
    }
    if (children.size() == 1) {
      String value = children.iterator().next().getAttributeValue(VALUE_ATTR);
      return value == null ? Collections.<String>emptyList() : Collections.singletonList(value);
    }
    List<String> values = ContainerUtil.newArrayListWithCapacity(children.size());
    for (Element child : children) {
      String value = child.getAttributeValue(VALUE_ATTR);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }

  public static void addChildrenWithValueAttribute(@NotNull Element parent,
                                                   @NotNull String childTagName,
                                                   @NotNull List<String> attrValues) {
    for (String value : attrValues) {
      if (value != null) {
        Element child = new Element(childTagName);
        child.setAttribute(VALUE_ATTR, value);
        parent.addContent(child);
      }
    }
  }

  @SuppressWarnings("deprecation")
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

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #readOption(Element, String)} (to be removed in IDEA 2019) */
  @Deprecated
  public static Element getOption(@NotNull Element parent, @NotNull String fieldName) {
    return readOption(parent, fieldName);
  }

  /** @deprecated use {@link #writeCustomField(Element, String, String)} (to be removed in IDEA 2019) */
  @Deprecated
  public static Element addElementWithValueAttribute(@NotNull Element parent, @NotNull String childTagName, @Nullable String attrValue) {
    writeCustomField(parent, childTagName, attrValue);
    return parent.getChild(childTagName);
  }

  /** @deprecated use {@link #readCustomField(Element, String)} (to be removed in IDEA 2019) */
  @Deprecated
  public static String getFirstChildValueAttribute(@NotNull Element parent, @NotNull String childTagName) {
    return readCustomField(parent, childTagName);
  }
  //</editor-fold>
}