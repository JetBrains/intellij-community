// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.XmlElement;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.XMap;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static com.intellij.util.xmlb.Constants.*;

final class MapBinding implements MultiNodeBinding, NestedBinding {
  private static final Logger LOG = Logger.getInstance(MapBinding.class);

  @SuppressWarnings("rawtypes")
  private static final Comparator<Object> KEY_COMPARATOR = (o1, o2) -> {
    if (o1 instanceof Comparable && o2 instanceof Comparable) {
      Comparable c1 = (Comparable)o1;
      Comparable c2 = (Comparable)o2;
      //noinspection unchecked
      return c1.compareTo(c2);
    }
    return 0;
  };

  private final MapAnnotation oldAnnotation;
  private final XMap annotation;

  @SuppressWarnings("rawtypes")
  private final @NotNull Class<? extends Map> mapClass;

  private Class<?> keyClass;
  private Class<?> valueClass;

  private Binding keyBinding;
  private Binding valueBinding;

  private final MutableAccessor accessor;

  MapBinding(@Nullable MutableAccessor accessor, @NotNull Class<? extends Map<?, ?>> mapClass) {
    this.accessor = accessor;

    oldAnnotation = accessor == null ? null : accessor.getAnnotation(MapAnnotation.class);
    annotation = accessor == null ? null : accessor.getAnnotation(XMap.class);
    this.mapClass = mapClass;
  }

  @Override
  public @NotNull MutableAccessor getAccessor() {
    return accessor;
  }

  @Override
  public void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    ParameterizedType type = (ParameterizedType)originalType;
    Type[] typeArguments = type.getActualTypeArguments();

    keyClass = ClassUtil.typeToClass(typeArguments[0]);
    Type valueType;
    if (typeArguments.length == 1) {
      String typeName = type.getRawType().getTypeName();
      if (typeName.equals("it.unimi.dsi.fastutil.objects.Object2IntMap") || typeName.equals("it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap")) {
        valueClass = Integer.class;
      }
      else {
        throw new UnsupportedOperationException("Value class is unknown for " + type.getTypeName());
      }

      valueType = Integer.class;
    }
    else {
      valueType = typeArguments[1];
      valueClass = ClassUtil.typeToClass(valueType);
    }

    keyBinding = serializer.getBinding(keyClass, typeArguments[0]);
    valueBinding = serializer.getBinding(valueClass, valueType);
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  private boolean isSortMap(Map<?, ?> map) {
    // for new XMap we do not sort LinkedHashMap
    if (map instanceof TreeMap || (annotation != null && map instanceof LinkedHashMap)) {
      return false;
    }
    return oldAnnotation == null || oldAnnotation.sortBeforeSave();
  }

  @Override
  public @Nullable Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    Element serialized = isSurroundWithTag() ? new Element(MAP) : (Element)context;
    assert serialized != null;

    @SuppressWarnings("rawtypes")
    Map map = (Map)o;
    Object[] keys = ArrayUtil.toObjectArray(map.keySet());
    if (isSortMap(map)) {
      Arrays.sort(keys, KEY_COMPARATOR);
    }

    for (Object k : keys) {
      Element entry = new Element(getEntryElementName());
      serialized.addContent(entry);

      serializeKeyOrValue(entry, getKeyAttributeName(), k, keyBinding, filter);
      serializeKeyOrValue(entry, getValueAttributeName(), map.get(k), valueBinding, filter);
    }

    return serialized == context ? null : serialized;
  }

  private boolean isSurroundWithTag() {
    if (annotation != null) {
      return false;
    }
    return oldAnnotation == null || oldAnnotation.surroundWithTag();
  }

  @NotNull
  String getEntryElementName() {
    if (annotation != null) {
      return annotation.entryTagName();
    }
    return oldAnnotation == null ? ENTRY : oldAnnotation.entryTagName();
  }

  private String getKeyAttributeName() {
    if (annotation != null) {
      return annotation.keyAttributeName();
    }
    return oldAnnotation == null ? KEY : oldAnnotation.keyAttributeName();
  }

  private String getValueAttributeName() {
    if (annotation != null) {
      return annotation.valueAttributeName();
    }
    return oldAnnotation == null ? VALUE : oldAnnotation.valueAttributeName();
  }

  @Override
  public @Nullable Object deserializeList(@Nullable Object context, @NotNull List<Element> elements) {
    List<Element> childNodes;
    if (isSurroundWithTag()) {
      assert elements.size() == 1;
      childNodes = elements.get(0).getChildren();
    }
    else {
      childNodes = elements;
    }
    return deserialize(context, childNodes);
  }

  @Override
  public @Nullable Object deserializeList2(@Nullable Object context, @NotNull List<XmlElement> elements) {
    List<XmlElement> childNodes;
    if (isSurroundWithTag()) {
      assert elements.size() == 1;
      childNodes = elements.get(0).children;
    }
    else {
      childNodes = elements;
    }
    return deserialize2(context, childNodes);
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull Element element) {
    return null;
  }

  @Override
  public Object deserializeUnsafe(Object context, @NotNull XmlElement element) {
    return null;
  }

  public @Nullable Object deserialize(@Nullable Object context, @NotNull Element element) {
    if (isSurroundWithTag()) {
      return deserialize(context, element.getChildren());
    }
    else {
      return deserialize(context, Collections.singletonList(element));
    }
  }

  @SuppressWarnings({"rawtypes", "DuplicatedCode"})
  private @Nullable Map<?, ?> deserialize(@Nullable Object context, @NotNull List<Element> childNodes) {
    // if accessor is null, it is sub-map and we must not use context
    Map map = accessor == null ? null : (Map<?, ?>)context;
    if (map != null) {
      if (ClassUtil.isMutableMap(map)) {
        map.clear();
      }
      else {
        map = null;
      }
    }

    for (Element childNode : childNodes) {
      if (!childNode.getName().equals(getEntryElementName())) {
        LOG.warn("unexpected entry for serialized Map will be skipped: " + childNode);
        continue;
      }

      if (map == null) {
        if (mapClass == Map.class) {
          map = new HashMap();
        }
        else {
          try {
            map = ReflectionUtil.newInstance(mapClass);
          }
          catch (Exception e) {
            LOG.warn(e);
            map = new HashMap();
          }
        }
      }

      //noinspection unchecked
      map.put(deserializeKeyOrValue(childNode, getKeyAttributeName(), context, keyBinding, keyClass),
              deserializeKeyOrValue(childNode, getValueAttributeName(), context, valueBinding, valueClass));
    }
    return map;
  }

  @SuppressWarnings({"rawtypes", "DuplicatedCode"})
  private @Nullable Map<?, ?> deserialize2(@Nullable Object context, @NotNull List<XmlElement> childNodes) {
    // if accessor is null, it is sub-map and we must not use context
    Map map = accessor == null ? null : (Map<?, ?>)context;
    if (map != null) {
      if (ClassUtil.isMutableMap(map)) {
        map.clear();
      }
      else {
        map = null;
      }
    }

    for (XmlElement childNode : childNodes) {
      if (!childNode.name.equals(getEntryElementName())) {
        LOG.warn("unexpected entry for serialized Map will be skipped: " + childNode);
        continue;
      }

      if (map == null) {
        if (mapClass == Map.class) {
          map = new HashMap();
        }
        else {
          try {
            map = ReflectionUtil.newInstance(mapClass);
          }
          catch (Exception e) {
            LOG.warn(e);
            map = new HashMap();
          }
        }
      }

      //noinspection unchecked
      map.put(deserializeKeyOrValue(childNode, getKeyAttributeName(), context, keyBinding, keyClass),
              deserializeKeyOrValue(childNode, getValueAttributeName(), context, valueBinding, valueClass));
    }
    return map;
  }

  private void serializeKeyOrValue(@NotNull Element entry,
                                   @NotNull String attributeName,
                                   @Nullable Object value,
                                   @Nullable Binding binding,
                                   @Nullable SerializationFilter filter) {
    if (value == null) {
      return;
    }

    if (binding == null) {
      entry.setAttribute(attributeName, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)));
    }
    else {
      Object serialized = binding.serialize(value, entry, filter);
      if (serialized != null) {
        if (isSurroundKey()) {
          Element container = new Element(attributeName);
          container.addContent((Content)serialized);
          entry.addContent(container);
        }
        else {
          entry.addContent((Content)serialized);
        }
      }
    }
  }

  private Object deserializeKeyOrValue(@NotNull Element entry,
                                       @NotNull String attributeName,
                                       Object context,
                                       @Nullable Binding binding,
                                       @NotNull Class<?> valueClass) {
    Attribute attribute = entry.getAttribute(attributeName);
    if (attribute != null) {
      return XmlSerializerImpl.convert(attribute.getValue(), valueClass);
    }
    else if (!isSurroundKey()) {
      assert binding != null;
      for (Element element : entry.getChildren()) {
        if (binding.isBoundTo(element)) {
          return binding.deserializeUnsafe(context, element);
        }
      }
    }
    else {
      Element entryChild = entry.getChild(attributeName);
      List<Element> children = entryChild == null ? Collections.emptyList() : entryChild.getChildren();
      if (children.isEmpty()) {
        return null;
      }
      else {
        assert binding != null;
        return Binding.deserializeList(binding, null, children);
      }
    }
    return null;
  }

  private Object deserializeKeyOrValue(@NotNull XmlElement entry,
                                       @NotNull String attributeName,
                                       Object context,
                                       @Nullable Binding binding,
                                       @NotNull Class<?> valueClass) {
    String attribute = entry.attributes.get(attributeName);
    if (attribute != null) {
      return XmlSerializerImpl.convert(attribute, valueClass);
    }
    else if (!isSurroundKey()) {
      assert binding != null;
      for (XmlElement element : entry.children) {
        if (binding.isBoundTo(element)) {
          return binding.deserializeUnsafe(context, element);
        }
      }
    }
    else {
      XmlElement entryChild = entry.getChild(attributeName);
      List<XmlElement> children = entryChild == null ? Collections.emptyList() : entryChild.children;
      if (children.isEmpty()) {
        return null;
      }
      else {
        assert binding != null;
        return Binding.deserializeList2(binding, null, children);
      }
    }
    return null;
  }

  private boolean isSurroundKey() {
    if (annotation != null) {
      return false;
    }
    return oldAnnotation == null || oldAnnotation.surroundKeyWithTag();
  }

  boolean isBoundToWithoutProperty(@NotNull String elementName) {
    if (annotation != null) {
      return elementName.equals(annotation.entryTagName());
    }
    else if (oldAnnotation != null && !oldAnnotation.surroundWithTag()) {
      return elementName.equals(oldAnnotation.entryTagName());
    }
    else {
      return elementName.equals(MAP);
    }
  }

  @Override
  public boolean isBoundTo(@NotNull Element element) {
    if (oldAnnotation != null && !oldAnnotation.surroundWithTag()) {
      return oldAnnotation.entryTagName().equals(element.getName());
    }
    else if (annotation != null) {
      return annotation.propertyElementName().equals(element.getName());
    }
    else {
      return element.getName().equals(MAP);
    }
  }

  @Override
  public boolean isBoundTo(@NotNull XmlElement element) {
    if (oldAnnotation != null && !oldAnnotation.surroundWithTag()) {
      return oldAnnotation.entryTagName().equals(element.name);
    }
    else if (annotation != null) {
      return annotation.propertyElementName().equals(element.name);
    }
    else {
      return element.name.equals(MAP);
    }
  }
}
