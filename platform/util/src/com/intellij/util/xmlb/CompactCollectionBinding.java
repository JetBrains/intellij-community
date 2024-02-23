// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.dom.XmlElement;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonPrimitive;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @see com.intellij.util.xmlb.annotations.CollectionBean
 */
final class CompactCollectionBinding implements NotNullDeserializeBinding, NestedBinding {
  private final String name;
  private final MutableAccessor accessor;

  CompactCollectionBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;
    name = accessor.getName();
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public @NotNull JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)accessor.read(bean);
    if (list.isEmpty()) {
      return new JsonArray(Collections.emptyList());
    }

    List<JsonElement> content = new ArrayList<>();
    for (String item : list) {
      content.add(JsonElementKt.JsonPrimitive(JDOMUtil.removeControlChars(item)));
    }
    return new JsonArray(content);
  }

  @Override
  public Object fromJson(@NotNull Object bean, @NotNull JsonElement element) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)bean;

    if (!(element instanceof JsonArray)) {
      // yes, `null` is also not expected
      BeanBindingKt.LOG.warn("Expected JsonArray but got " + element);
      return list;
    }

    list.clear();
    for (JsonElement child : (JsonArray)element) {
      list.add(((JsonPrimitive)child).getContent());
    }
    return list;
  }

  @Override
  public void serialize(@NotNull Object bean, @NotNull Element parent, @Nullable SerializationFilter filter) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)accessor.read(bean);
    if (list == null) {
      return;
    }

    Element result = new Element(name);
    parent.addContent(result);

    if (list.isEmpty()) {
      return;
    }

    for (String item : list) {
      result.addContent(new Element("item").setAttribute("value", JDOMUtil.removeControlChars(item)));
    }
  }

  @Override
  public @NotNull Object deserialize(Object bean, @NotNull Element element) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)accessor.read(bean);
    list.clear();
    if (element.getName().equals(name)) {
      for (Element item : element.getChildren("item")) {
        ContainerUtil.addIfNotNull(list, item.getAttributeValue("value"));
      }
    }
    else {
      // JDOMExternalizableStringList format
      Element value = element.getChild("value");
      if (value != null) {
        value = value.getChild("list");
      }
      if (value != null) {
        for (Element item : value.getChildren("item")) {
          ContainerUtil.addIfNotNull(list, item.getAttributeValue("itemvalue"));
        }
      }
    }
    return list;
  }

  @Override
  public @NotNull Object deserialize(Object context, @NotNull XmlElement element) {
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)context;
    list.clear();
    if (element.name.equals(name)) {
      for (XmlElement item : element.children) {
        if (item.name.equals("item")) {
          String v = item.attributes.get("value");
          if (v != null) {
            list.add(v);
          }
        }
      }
    }
    else {
      // JDOMExternalizableStringList format
      XmlElement value = element.getChild("value");
      if (value != null) {
        value = value.getChild("list");
      }
      if (value != null) {
        for (XmlElement item : value.children) {
          if (item.name.equals("item")) {
            String v = item.getAttributeValue("itemvalue");
            if (v != null) {
              list.add(v);
            }
          }
        }
      }
    }
    return list;
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    String elementName = element.getName();
    if (isNameEqual(elementName)) {
      return true;
    }
    else if (elementName.equals(Constants.OPTION)) {
      // JDOMExternalizableStringList format
      return isNameEqual(element.getAttributeValue(Constants.NAME));
    }
    return false;
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    String elementName = element.name;
    if (isNameEqual(elementName)) {
      return true;
    }
    else if (elementName.equals(Constants.OPTION)) {
      // JDOMExternalizableStringList format
      return isNameEqual(element.getAttributeValue(Constants.NAME));
    }
    return false;
  }

  private boolean isNameEqual(@Nullable String value) {
    if (value == null) {
      return false;
    }
    else if (value.equals(name)) {
      return true;
    }
    else if (value.length() == (name.length() + 2) && value.startsWith("my")) {
      return Character.isUpperCase(value.charAt(2)) && value.regionMatches(true, 2, name, 0, name.length());
    }
    else {
      return false;
    }
  }
}
