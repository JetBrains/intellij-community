/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMExternalizerUtil {
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
}
