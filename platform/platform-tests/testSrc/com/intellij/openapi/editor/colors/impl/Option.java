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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey.Malenkov
 */
final class Option {
  private final HashMap<String, String> myMap = new HashMap<>();

  Option(String value) {
    if (value != null) {
      myMap.put("value", value);
    }
  }

  Option(String name, String value) {
    this(value);
    if (name != null) {
      myMap.put("name", name);
    }
  }

  Option os(@NotNull String value) {
    myMap.put("windows", value);
    myMap.put("linux", value);
    myMap.put("mac", value);
    return this;
  }

  Option put(@NotNull String key, @NotNull String value) {
    myMap.put(key, value);
    return this;
  }

  @NotNull
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("<option");
    for (Map.Entry<String, String> entry : myMap.entrySet()) {
      sb.append(' ').append(entry.getKey()).append('=');
      sb.append('"').append(entry.getValue()).append('"');
    }
    return sb.append("/>").toString();
  }

  static Element element(Object object) throws Exception {
    String xml = object == null ? null : object.toString();
    System.out.println(xml);
    return xml == null ? null : JDOMUtil.loadDocument(xml).getRootElement();
  }
}
