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
package com.intellij.openapi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JDOMExternalizer {
  private JDOMExternalizer() {
  }

  public static void write(Element root, @NonNls String name, String value) {
    @NonNls Element element = new Element("setting");
    element.setAttribute("name", name);
    element.setAttribute("value", value == null ? "" : value);
    root.addContent(element);
  }

  public static void write(Element root, @NonNls String name, boolean value) {
    write(root, name, Boolean.toString(value));
  }
  public static void write(Element root, String name, int value) {
    write(root, name, Integer.toString(value));
  }

  public static boolean readBoolean(Element root, @NonNls String name) {
    return Boolean.valueOf(readString(root, name)).booleanValue();
  }
  public static int readInteger(Element root, String name, int defaultValue) {
    try {
      return Integer.valueOf(readString(root, name)).intValue();
    }
    catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  public static String readString(@NonNls Element root, @NonNls String name) {
    List list = root.getChildren("setting");
    for (Object aList : list) {
      @NonNls Element element = (Element)aList;
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, name)) {
        return element.getAttributeValue("value");
      }
    }
    return null;
  }

  public static void writeMap(Element root, Map<String, String>map, @NonNls @Nullable String rootName, @NonNls String entryName) {
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
    if (mapRoot == null) return;
    for (@NonNls Element element : (List<Element>)mapRoot.getChildren(entryName)) {
      final String name = element.getAttributeValue("name");
      if (name != null) {
        final String value = element.getAttributeValue("value");
        map.put(name, value);
      }
    }
  }
}