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
package com.intellij.ide.util;

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class PropertiesComponentImpl extends PropertiesComponent implements PersistentStateComponent<Element> {
  private final HashMap<String, String> myMap = new HashMap<String, String>();
  @NonNls private static final String ELEMENT_PROPERTY = "property";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";


  @NotNull
  public String getComponentName() {
    return "PropertiesComponent";
  }

  PropertiesComponentImpl() {
  }



  public Element getState() {
    Element parentNode = new Element("state");
    for (final String key : myMap.keySet()) {
      String value = myMap.get(key);
      if (value != null) {
        Element element = new Element(ELEMENT_PROPERTY);
        element.setAttribute(ATTRIBUTE_NAME, key);
        element.setAttribute(ATTRIBUTE_VALUE, value);
        parentNode.addContent(element);
      }
    }
    return parentNode;
  }

  public void loadState(final Element parentNode) {
    myMap.clear();
    for (final Object o : parentNode.getChildren(ELEMENT_PROPERTY)) {
      Element e = (Element)o;

      String name = e.getAttributeValue(ATTRIBUTE_NAME);
      String value = e.getAttributeValue(ATTRIBUTE_VALUE);

      if (name != null) {
        myMap.put(name, value);
      }
    }
  }

  public String getValue(String name) {
    return myMap.get(name);
  }

  public void setValue(String name, String value) {
    myMap.put(name, value);
  }

  @Override
  public void unsetValue(String name) {
    myMap.remove(name);
  }

  public boolean isValueSet(String name) {
    return myMap.containsKey(name);
  }

  @Override
  public String[] getValues(@NonNls String name) {
    final String value = getValue(name);
    return value != null ? value.split("\n") : null;
  }

  @Override
  public void setValues(@NonNls String name, String[] values) {
    if (values == null) {
      setValue(name, null);
    } else {
      setValue(name, StringUtil.join(values, "\n"));
    }
  }
}
