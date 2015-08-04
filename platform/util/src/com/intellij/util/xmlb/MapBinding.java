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
package com.intellij.util.xmlb;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import gnu.trove.THashMap;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static com.intellij.util.xmlb.Constants.*;

class MapBinding extends Binding implements MultiNodeBinding, MainBinding {
  private static final Comparator<Object> KEY_COMPARATOR = new Comparator<Object>() {
    @SuppressWarnings({"unchecked", "NullableProblems"})
    @Override
    public int compare(Object o1, Object o2) {
      if (o1 instanceof Comparable && o2 instanceof Comparable) {
        Comparable c1 = (Comparable)o1;
        Comparable c2 = (Comparable)o2;
        return c1.compareTo(c2);
      }
      return 0;
    }
  };

  private final MapAnnotation myMapAnnotation;

  private Class<?> keyClass;
  private Class<?> valueClass;

  private Binding keyBinding;
  private Binding valueBinding;

  public MapBinding(@NotNull MutableAccessor accessor) {
    super(accessor);

    myMapAnnotation = accessor.getAnnotation(MapAnnotation.class);
  }

  @Override
  public void init(@NotNull Type originalType) {
    ParameterizedType type = (ParameterizedType)originalType;
    Type[] typeArguments = type.getActualTypeArguments();

    keyClass = XmlSerializerImpl.typeToClass(typeArguments[0]);
    valueClass = XmlSerializerImpl.typeToClass(typeArguments[1]);

    keyBinding = XmlSerializerImpl.getClassBinding(keyClass, typeArguments[0], null);
    valueBinding = XmlSerializerImpl.getClassBinding(valueClass, typeArguments[1], null);
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @NotNull SerializationFilter filter) {
    Element serialized = myMapAnnotation == null || myMapAnnotation.surroundWithTag() ? new Element(MAP) : (Element)context;
    assert serialized != null;

    Map map = (Map)o;
    Object[] keys = ArrayUtil.toObjectArray(map.keySet());
    if (!(map instanceof TreeMap) && (myMapAnnotation == null || myMapAnnotation.sortBeforeSave())) {
      Arrays.sort(keys, KEY_COMPARATOR);
    }

    for (Object k : keys) {
      Element entry = new Element(getEntryAttributeName());
      serialized.addContent(entry);

      serializeKeyOrValue(entry, getKeyAttributeName(), k, keyBinding, filter);
      serializeKeyOrValue(entry, getValueAttributeName(), map.get(k), valueBinding, filter);
    }

    return serialized == context ? null : serialized;
  }

  private String getEntryAttributeName() {
    return myMapAnnotation == null ? ENTRY : myMapAnnotation.entryTagName();
  }

  private String getKeyAttributeName() {
    return myMapAnnotation == null ? KEY : myMapAnnotation.keyAttributeName();
  }

  private String getValueAttributeName() {
    return myMapAnnotation == null ? VALUE : myMapAnnotation.valueAttributeName();
  }

  @Nullable
  @Override
  public Object deserializeList(Object context, @NotNull List<Element> elements) {
    List<Element> childNodes;
    if (myMapAnnotation == null || myMapAnnotation.surroundWithTag()) {
      assert elements.size() == 1;
      childNodes = elements.get(0).getChildren();
    }
    else {
      childNodes = elements;
    }
    return deserialize(context, childNodes);
  }

  @Override
  @Nullable
  public Object deserialize(Object context, @NotNull Element element) {
    if (myMapAnnotation == null || myMapAnnotation.surroundWithTag()) {
      return deserialize(context, element.getChildren());
    }
    else {
      return deserialize(context, Collections.singletonList(element));
    }
  }

  @Nullable
  private Map deserialize(@Nullable Object context, @NotNull List<Element> childNodes) {
    Map map = (Map)context;
    if (map != null) {
      map.clear();
    }

    for (Element childNode : childNodes) {
      if (!childNode.getName().equals(getEntryAttributeName())) {
        LOG.warn("unexpected entry for serialized Map will be skipped: " + childNode);
        continue;
      }

      if (map == null) {
        map = new THashMap();
      }

      //noinspection unchecked
      map.put(deserializeKeyOrValue(childNode, getKeyAttributeName(), context, keyBinding, keyClass),
              deserializeKeyOrValue(childNode, getValueAttributeName(), context, valueBinding, valueClass));
    }
    return map;
  }

  private void serializeKeyOrValue(@NotNull Element entry, @NotNull String attributeName, @Nullable Object value, @Nullable Binding binding, @NotNull SerializationFilter filter) {
    if (value == null) {
      return;
    }

    if (binding == null) {
      entry.setAttribute(attributeName, XmlSerializerImpl.convertToString(value));
    }
    else {
      Object serialized = binding.serialize(value, entry, filter);
      if (serialized != null) {
        if (myMapAnnotation != null && !myMapAnnotation.surroundKeyWithTag()) {
          entry.addContent((Content)serialized);
        }
        else {
          Element container = new Element(attributeName);
          container.addContent((Content)serialized);
          entry.addContent(container);
        }
      }
    }
  }

  private Object deserializeKeyOrValue(@NotNull Element entry, @NotNull String attributeName, Object context, @Nullable Binding binding, @NotNull Class<?> valueClass) {
    Attribute attribute = entry.getAttribute(attributeName);
    if (attribute != null) {
      return XmlSerializerImpl.convert(attribute.getValue(), valueClass);
    }
    else if (myMapAnnotation != null && !myMapAnnotation.surroundKeyWithTag()) {
      assert binding != null;
      for (Element element : entry.getChildren()) {
        if (binding.isBoundTo(element)) {
          return binding.deserialize(context, element);
        }
      }
    }
    else {
      Element entryChild = entry.getChild(attributeName);
      List<Element> children = entryChild == null ? Collections.<Element>emptyList() : entryChild.getChildren();
      if (children.isEmpty()) {
        return null;
      }
      else {
        assert binding != null;
        return Binding.deserializeList(binding, context, children);
      }
    }
    return null;
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    if (myMapAnnotation != null && !myMapAnnotation.surroundWithTag()) {
      return myMapAnnotation.entryTagName().equals(element.getName());
    }
    else {
      return element.getName().equals(MAP);
    }
  }
}
