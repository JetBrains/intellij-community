// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.Constants;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Use XmlSerializer instead.
 */
@Deprecated
public class JDOMExternalizer {
  private JDOMExternalizer() {
  }

  /**
   * @deprecated Use XmlSerializer instead.
   */
  @Deprecated
  public static void write(Element root, @NonNls String name, String value) {
    @NonNls Element element = new Element("setting");
    element.setAttribute(Constants.NAME, name);
    element.setAttribute(Constants.VALUE, value == null ? "" : value);
    root.addContent(element);
  }

  @Deprecated
  public static void write(Element root, @NonNls String name, boolean value) {
    write(root, name, Boolean.toString(value));
  }

  @Deprecated
  public static void write(Element root, String name, int value) {
    write(root, name, Integer.toString(value));
  }

  public static boolean readBoolean(Element root, @NonNls String name) {
    return Boolean.parseBoolean(readString(root, name));
  }

  public static int readInteger(Element root, String name, int defaultValue) {
    return StringUtilRt.parseInt(readString(root, name), defaultValue);
  }

  @Nullable
  public static String readString(@NonNls Element root, @NonNls String name) {
    for (Element element : root.getChildren("setting")) {
      if (Comparing.strEqual(element.getAttributeValue(Constants.NAME), name)) {
        return element.getAttributeValue(Constants.VALUE);
      }
    }
    return null;
  }

  public static void writeMap(Element root, Map<String, String> map, @NonNls @Nullable String rootName, @NonNls String entryName) {
    Element mapRoot;
    if (StringUtil.isNotEmpty(rootName)) {
      mapRoot = new Element(rootName);
      root.addContent(mapRoot);
    }
    else {
      mapRoot = root;
    }
    final String[] names = ArrayUtil.toStringArray(map.keySet());
    Arrays.sort(names);
    for (String name : names) {
      @NonNls final Element element = new Element(entryName);
      element.setAttribute("name", name);
      final String value = map.get(name);
      if (value != null) {
        element.setAttribute("value", value);
      }
      mapRoot.addContent(element);
    }
  }

  public static void readMap(Element root, Map<String, String> map, @NonNls @Nullable String rootName, @NonNls String entryName) {
    Element mapRoot;
    if (StringUtil.isNotEmpty(rootName)) {
      mapRoot = root.getChild(rootName);
    }
    else {
      mapRoot = root;
    }
    if (mapRoot == null) {
      return;
    }

    for (@NonNls Element element : mapRoot.getChildren(entryName)) {
      String name = element.getAttributeValue("name");
      if (name != null) {
        map.put(name, element.getAttributeValue("value"));
      }
    }
  }

  /**
   * Saves a pack of strings to some attribute. I.e: [tag attr="value"]
   * @param parent parent element (where to add newly created tags)
   * @param nodeName node name (tag, in our example)
   * @param attrName attribute name (attr, in our example)
   * @param values a pack of values to add
   * @see #loadStringsList(org.jdom.Element, String, String)
   */
  public static void saveStringsList(@NotNull final Element parent,
                                     @NotNull final String nodeName,
                                     @NotNull final String attrName,
                                     @NotNull final String... values) {
    for (final String value : values) {
      final Element node = new Element(nodeName);
      node.setAttribute(attrName, value);
      parent.addContent(node);
    }
  }

  @NotNull
  public static List<String> loadStringsList(Element element, String rootName, String attrName) {
    final List<String> paths = new LinkedList<String>();
    if (element != null) {
      @NotNull final List list = element.getChildren(rootName);
      for (Object o : list) {
        paths.add(((Element)o).getAttribute(attrName).getValue());
      }
    }
    return paths;
  }
}