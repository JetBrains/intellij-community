// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.MutableAccessor;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonNull;
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
final class CompactCollectionBinding implements Binding, NestedBinding {
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
  public @NotNull JsonElement deserializeToJson(@NotNull Element element) {
    List<JsonElement> content = new ArrayList<>(element.getContentSize());
    for (Element o : element.getChildren()) {
      content.add(JsonElementKt.JsonPrimitive(o.getAttributeValue(Constants.VALUE)));
    }
    return new JsonArray(content);
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
  public void setFromJson(@NotNull Object bean, @NotNull JsonElement element) {
    if (element == JsonNull.INSTANCE) {
      //noinspection unchecked
      ((List<String>)accessor.read(bean)).clear();
      return;
    }

    if (!(element instanceof JsonArray)) {
      BeanBindingKt.LOG.warn("Expected JsonArray but got " + element);
      return;
    }

    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)accessor.read(bean);
    list.clear();
    for (JsonElement child : (JsonArray)element) {
      list.add(((JsonPrimitive)child).getContent());
    }
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
      result.addContent(new Element("item").setAttribute(Constants.VALUE, JDOMUtil.removeControlChars(item)));
    }
  }

  @Override
  public @NotNull <T> Object deserialize(@Nullable Object bean, @NotNull T element, @NotNull DomAdapter<T> adapter) {
    assert bean != null;
    @SuppressWarnings("unchecked")
    List<String> list = (List<String>)accessor.read(bean);
    list.clear();
    if (adapter.getName(element).equals(name)) {
      for (T item : adapter.getChildren(element)) {
        if (adapter.getName(item).equals("item")) {
          String v = adapter.getAttributeValue(item, Constants.VALUE);
          if (v != null) {
            list.add(v);
          }
        }
      }
    }
    else {
      // JDOMExternalizableStringList format
      T value = adapter.getChild(element, Constants.VALUE);
      if (value != null) {
        value = adapter.getChild(value, "list");
      }
      if (value != null) {
        for (T item : adapter.getChildren(value)) {
          String v = adapter.getName(item).equals("item") ? adapter.getAttributeValue(item, "itemvalue") : null;
          if (v != null) {
            list.add(v);
          }
        }
      }
    }
    return list;
  }

  @Override
  public <T> boolean isBoundTo(@NotNull T element, @NotNull DomAdapter<T> adapter) {
    String elementName = adapter.getName(element);
    if (isNameEqual(elementName)) {
      return true;
    }
    else if (elementName.equals(Constants.OPTION)) {
      // JDOMExternalizableStringList format
      return isNameEqual(adapter.getAttributeValue(element, Constants.NAME));
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
