// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xmlb.annotations.Tag;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.xmlb.JsonDomKt.jdomToJson;
import static com.intellij.util.xmlb.JsonDomKt.jsonDomToXml;

final class JDOMElementBinding implements MultiNodeBinding, NestedBinding {
  private final String tagName;
  private final MutableAccessor accessor;

  JDOMElementBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;

    Tag tag = this.accessor.getAnnotation(Tag.class);
    String tagName = tag == null ? null : tag.value();
    this.tagName = tagName == null || tagName.isEmpty() ? this.accessor.getName() : tagName;
  }

  @Override
  public @NotNull JsonElement deserializeToJson(@NotNull Element element) {
    return jdomToJson(element);
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public @Nullable JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return null;
    }

    if (value instanceof Element) {
      Element element = (Element)value;
      if (!element.getName().equals(tagName)) {
        element = ((Element)value).clone().setName(tagName);
      }
      return jdomToJson(element);
    }
    if (value instanceof Element[]) {
      Element[] elements = (Element[])value;
      if (elements.length == 0) {
        return null;
      }
      else {
        List<JsonElement> result = new ArrayList<>();
        for (Element element : elements) {
          result.add(jdomToJson(element.getName().equals(tagName) ? element : element.clone().setName(tagName)));
        }
        return new JsonArray(result);
      }
    }
    else {
      return null;
    }
  }

  @Override
  public void setFromJson(@NotNull Object bean, @NotNull JsonElement element) {
    if (element instanceof JsonObject) {
      accessor.set(bean, jsonDomToXml((JsonObject)element));
    }
    else if (element instanceof JsonArray) {
      JsonArray jsonArray = (JsonArray)element;
      List<Element> result = new ArrayList<>(jsonArray.getSize());
      for (JsonElement o : jsonArray) {
        result.add(jsonDomToXml((JsonObject)o));
      }
      accessor.set(bean, result.toArray(new Element[0]));
    }
  }

  @Override
  public void serialize(@NotNull Object bean, @NotNull Element parent, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return;
    }

    if (value instanceof Element) {
      parent.addContent(((Element)value).clone().setName(tagName));
    }
    else if (value instanceof Element[]) {
      Element[] elements = (Element[])value;
      if (elements.length != 0) {
        List<Element> result = new ArrayList<>(elements.length);
        for (Element element : elements) {
          result.add(element.clone().setName(tagName));
        }
        parent.addContent(result);
      }
    }
    else {
      throw new XmlSerializationException("org.jdom.Element expected but " + value + " found");
    }
  }

  @Override
  public boolean isSurroundWithTag() {
    return false;
  }

  @Override
  public <T> @NotNull Object deserializeList(@Nullable Object currentValue, @NotNull List<? extends T> elements, @NotNull DomAdapter<T> adapter) {
    assert currentValue != null;
    if (adapter != JdomAdapter.INSTANCE) {
      throw new UnsupportedOperationException("XmlElement is not supported by JDOMElementBinding");
    }

    if (accessor.getValueClass().isArray()) {
      //noinspection SuspiciousToArrayCall
      accessor.set(currentValue, elements.toArray(new Element[0]));
    }
    else {
      accessor.set(currentValue, elements.get(0));
    }
    return currentValue;
  }

  @Override
  public @NotNull JsonElement doDeserializeListToJson(@NotNull List<? extends Element> elements) {
    List<JsonElement> result = new ArrayList<>(elements.size());
    for (Element element : elements) {
      result.add(jdomToJson(element));
    }
    return new JsonArray(result);
  }

  @Override
  public boolean isMulti() {
    return accessor.getValueClass().isArray();
  }

  @Override
  public @NotNull <T> Object deserialize(@Nullable Object context, @NotNull T element, @NotNull DomAdapter<T> adapter) {
    assert context != null;
    accessor.set(context, element);
    return context;
  }

  @Override
  public <T> boolean isBoundTo(@NotNull T element, @NotNull DomAdapter<T> adapter) {
    return adapter.getName(element).equals(tagName);
  }
}
