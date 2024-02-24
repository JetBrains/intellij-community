// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.Tag;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonPrimitive;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.SafeStAXStreamBuilderKt.buildNsUnawareJdom;

final class JDOMElementBinding implements MultiNodeBinding, NestedBinding, NotNullDeserializeBinding {
  private final String tagName;
  private final MutableAccessor accessor;

  JDOMElementBinding(@NotNull MutableAccessor accessor) {
    this.accessor = accessor;

    Tag tag = this.accessor.getAnnotation(Tag.class);
    String tagName = tag == null ? null : tag.value();
    this.tagName = tagName == null || tagName.isEmpty() ? this.accessor.getName() : tagName;
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Nullable
  @Override
  public JsonElement toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    Object value = accessor.read(bean);
    if (value == null) {
      return null;
    }

    if (value instanceof Element) {
      return JsonElementKt.JsonPrimitive(JDOMUtil.writeElement((Element)value));
    }
    if (value instanceof Element[]) {
      List<JsonElement> result = new ArrayList<>();
      for (Element element : ((Element[])value)) {
        result.add(JsonElementKt.JsonPrimitive(JDOMUtil.writeElement(element)));
      }
      return new JsonArray(result);
    }
    else {
      return null;
    }
  }

  @Override
  public void setFromJson(@NotNull Object bean, @NotNull JsonElement element) {
    if (element instanceof JsonPrimitive) {
      try {
        accessor.set(bean, buildNsUnawareJdom(new StringReader(((JsonPrimitive)element).getContent())));
      }
      catch (XMLStreamException e) {
        throw new RuntimeException(e);
      }
    }
    else if (element instanceof JsonArray) {
      List<Element> result = new ArrayList<>();
      for (JsonElement o : ((JsonArray)element)) {
        try {
          result.add(buildNsUnawareJdom(new StringReader(((JsonPrimitive)o).getContent())));
        }
        catch (XMLStreamException e) {
          throw new RuntimeException(e);
        }
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
      Element targetElement = ((Element)value).clone();
      assert targetElement != null;
      targetElement.setName(tagName);
      parent.addContent(targetElement);
    }
    else if (value instanceof Element[]) {
      List<Element> result = new ArrayList<>();
      for (Element element : ((Element[])value)) {
        result.add(element.clone().setName(tagName));
      }
      parent.addContent(result);
    }
    else {
      throw new XmlSerializationException("org.jdom.Element expected but " + value + " found");
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> @NotNull Object deserializeList(@Nullable Object bean, @NotNull List<? extends T> elements, @NotNull DomAdapter<T> adapter) {
    assert bean != null;
    if (adapter == JdomAdapter.INSTANCE) {
      return deserializeJdomList(bean, (List<Element>)elements);
    }
    else {
      return deserializeList(bean, (List<XmlElement>)elements);
    }
  }

  @NotNull Object deserializeJdomList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<? extends Element> elements) {
    if (accessor.getValueClass().isArray()) {
      accessor.set(context, elements.toArray(new Element[0]));
    }
    else {
      accessor.set(context, elements.get(0));
    }
    return context;
  }

  public @NotNull Object deserializeList(@SuppressWarnings("NullableProblems") @NotNull Object context, @NotNull List<XmlElement> elements) {
    throw new UnsupportedOperationException("XmlElement is not supported by JDOMElementBinding");
  }

  @Override
  public boolean isMulti() {
    return true;
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
