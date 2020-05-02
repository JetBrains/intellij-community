// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jdom.Verifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PropertiesComponentImpl extends PropertiesComponent implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(PropertiesComponentImpl.class);

  private final Map<String, String> myMap = new ConcurrentHashMap<>();

  @NonNls private static final String ELEMENT_PROPERTY = "property";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";

  PropertiesComponentImpl() {
  }

  private void doPut(@NotNull String key, @NotNull String value) {
    String reason = Verifier.checkCharacterData(key);
    if (reason != null) {
      LOG.error(reason);
    }
    myMap.put(key, value);
    incModificationCount();
  }

  @Override
  public Element getState() {
    Element parentNode = new Element("state");
    List<String> keys = new ArrayList<>(myMap.keySet());
    keys.sort(null);
    for (final String key : keys) {
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

  @Override
  public void loadState(final @NotNull Element parentNode) {
    myMap.clear();
    for (Element e : parentNode.getChildren(ELEMENT_PROPERTY)) {
      String name = e.getAttributeValue(ATTRIBUTE_NAME);
      if (name != null) {
        myMap.put(name, e.getAttributeValue(ATTRIBUTE_VALUE));
      }
    }
  }

  @Override
  public String getValue(@NotNull String name) {
    return myMap.get(name);
  }

  @Override
  public void setValue(@NotNull String name, @Nullable String value) {
    if (value == null) {
      unsetValue(name);
    }
    else {
      doPut(name, value);
    }
  }

  @Override
  public void setValue(@NotNull String name, @Nullable String value, @Nullable String defaultValue) {
    if (value == null || value.equals(defaultValue)) {
      unsetValue(name);
    }
    else {
      doPut(name, value);
    }
  }

  @Override
  public void setValue(@NotNull String name, float value, float defaultValue) {
    if (value == defaultValue) {
      unsetValue(name);
    }
    else {
      doPut(name, String.valueOf(value));
    }
  }

  @Override
  public void setValue(@NotNull String name, int value, int defaultValue) {
    if (value == defaultValue) {
      unsetValue(name);
    }
    else {
      doPut(name, String.valueOf(value));
    }
  }

  @Override
  public void setValue(@NotNull String name, boolean value, boolean defaultValue) {
    if (value == defaultValue) {
      unsetValue(name);
    }
    else {
      setValue(name, String.valueOf(value));
    }
  }

  @Override
  public void unsetValue(@NotNull String name) {
    myMap.remove(name);
    incModificationCount();
  }

  @Override
  public boolean isValueSet(@NotNull String name) {
    return myMap.containsKey(name);
  }

  @Override
  public String @Nullable [] getValues(@NotNull @NonNls String name) {
    final String value = getValue(name);
    return value != null ? value.split("\n") : null;
  }

  @Override
  public void setValues(@NotNull @NonNls String name, String[] values) {
    if (values == null) {
      setValue(name, null);
    }
    else {
      setValue(name, StringUtil.join(values, "\n"));
    }
  }
}