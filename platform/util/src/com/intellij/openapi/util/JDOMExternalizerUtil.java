// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.xmlb.Constants;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JDOMExternalizerUtil {
  /**
   * Adds the {@code <option name="{fieldName}" value="{value}"/>} element to the parent.
   */
  public static void writeField(@NotNull Element parent, @NotNull @NonNls String fieldName, @Nullable String value) {
    Element element = new Element(Constants.OPTION);
    element.setAttribute(Constants.NAME, fieldName);
    element.setAttribute(Constants.VALUE, value == null ? "" : value);
    parent.addContent(element);
  }

  /**
   * Adds the {@code <option name="{fieldName}" value="{value}"/>} element to the parent when the value differs from the default.
   */
  public static void writeField(@NotNull Element parent, @NotNull @NonNls String fieldName, @Nullable String value, @NotNull String defaultValue) {
    if (!defaultValue.equals(value)) {
      writeField(parent, fieldName, value);
    }
  }

  @NotNull
  public static String readField(@NotNull Element parent, @NotNull @NonNls String fieldName, @NotNull String defaultValue) {
    String val = readField(parent, fieldName);
    return val == null ? defaultValue : val;
  }

  @Nullable
  public static String readField(@NotNull Element parent, @NonNls @NotNull String fieldName) {
    for (Element element : parent.getChildren(Constants.OPTION)) {
      if (fieldName.equals(element.getAttributeValue(Constants.NAME))) {
        return element.getAttributeValue(Constants.VALUE);
      }
    }
    return null;
  }

  /**
   * Adds the {@code <option name="{fieldName}"/>} element to the parent and returns the created element.
   */
  @NotNull
  public static Element writeOption(@NotNull Element parent, @NotNull String fieldName) {
    Element element = new Element(Constants.OPTION);
    element.setAttribute(Constants.NAME, fieldName);
    parent.addContent(element);
    return element;
  }

  @Nullable
  public static Element readOption(@NotNull Element parent, @NotNull String fieldName) {
    for (Element element : parent.getChildren(Constants.OPTION)) {
      if (fieldName.equals(element.getAttributeValue(Constants.NAME))) {
        return element;
      }
    }
    return null;
  }

  /**
   * Adds the {@code <{tagName} value="{value}"/>} element to the parent (or just {@code <{tagName}"/>} if the value is {@code null}).
   */
  public static void writeCustomField(@NotNull Element parent, @NotNull String tagName, @Nullable String value) {
    Element element = new Element(tagName);
    if (value != null) {
      element.setAttribute(Constants.VALUE, value);
    }
    parent.addContent(element);
  }

  @Nullable
  public static String readCustomField(@NotNull Element parent, @NotNull String tagName) {
    Element element = parent.getChild(tagName);
    return element != null ? element.getAttributeValue(Constants.VALUE) : null;
  }

  @NotNull
  public static List<String> getChildrenValueAttributes(@NotNull Element parent, @NotNull String childTagName) {
    List<Element> children = parent.getChildren(childTagName);
    if (children.isEmpty()) {
      return Collections.emptyList();
    }
    if (children.size() == 1) {
      String value = children.iterator().next().getAttributeValue(Constants.VALUE);
      return value == null ? Collections.emptyList() : Collections.singletonList(value);
    }
    List<String> values = new ArrayList<>(children.size());
    for (Element child : children) {
      String value = child.getAttributeValue(Constants.VALUE);
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
        child.setAttribute(Constants.VALUE, value);
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
  /** @deprecated use {@link #writeCustomField(Element, String, String)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static Element addElementWithValueAttribute(@NotNull Element parent, @NotNull String childTagName, @Nullable String attrValue) {
    writeCustomField(parent, childTagName, attrValue);
    return parent.getChild(childTagName);
  }

  /** @deprecated use {@link #readCustomField(Element, String)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static String getFirstChildValueAttribute(@NotNull Element parent, @NotNull String childTagName) {
    return readCustomField(parent, childTagName);
  }
  //</editor-fold>
}
