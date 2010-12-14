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

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMExternalizerUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.JDOMExternalizerUtil");
    
  public static void writeField(Element root, @NonNls String fieldName, String value) {
    Element element = new Element("option");
    element.setAttribute("name", fieldName);
    element.setAttribute("value", value == null ? "" : value);
    root.addContent(element);
  }

  @NotNull
  public static String readField(Element parent, @NonNls String fieldName, @NotNull String defaultValue) {
    String val = readField(parent, fieldName);
    if (val != null) {
      return val;
    } else {
      return defaultValue;
    }
  }

  @Nullable
  public static String readField(Element parent, @NonNls String fieldName) {
    List list = parent.getChildren("option");
    for (int i = 0; i < list.size(); i++) {
      Element element = (Element)list.get(i);
      String childName = element.getAttributeValue("name");
      if (Comparing.strEqual(childName, fieldName)) {
        return element.getAttributeValue("value");
      }
    }
    return null;
  }
}
