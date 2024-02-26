// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.dom.XmlElement;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.XMap;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import static com.intellij.util.xmlb.Constants.*;
import static com.intellij.util.xmlb.JsonHelperKt.fromJsonPrimitive;
import static com.intellij.util.xmlb.JsonHelperKt.primitiveToJsonElement;

@SuppressWarnings("LoggingSimilarMessage")
final class MapBinding implements MultiNodeBinding, RootBinding {
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

  MapBinding(@Nullable MapAnnotation oldAnnotation, @Nullable XMap newAnnotation, @NotNull Class<? extends Map<?, ?>> mapClass) {
    this.oldAnnotation = oldAnnotation;
    annotation = newAnnotation;
    this.mapClass = mapClass;
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
  public @NotNull JsonObject toJson(@NotNull Object bean, @Nullable SerializationFilter filter) {
    @SuppressWarnings("rawtypes")
    Map map = (Map)bean;

    if (map.isEmpty()) {
      return new JsonObject(Collections.emptyMap());
    }

    Object[] keys = ArrayUtil.toObjectArray(map.keySet());
    if (isSortMap(map)) {
      Arrays.sort(keys, KEY_COMPARATOR);
    }

    Map<String, JsonElement> content = new LinkedHashMap<>();
    for (Object k : keys) {
      JsonElement kJ = keyOrValueToJson(k, keyBinding, filter);
      JsonElement vJ = keyOrValueToJson(k, valueBinding, filter);
      // todo non-primitive keys
      content.put(kJ == null ? null : ((JsonPrimitive)kJ).getContent(), vJ);
    }
    return new JsonObject(content);
  }

  @SuppressWarnings({"rawtypes", "DuplicatedCode"})
  @Override
  public @Nullable Object fromJson(@Nullable Object currentValue, @NotNull JsonElement element) {
    // if accessor is null, it is a sub-map, and we must not use context
    Map map = (Map<?, ?>)currentValue;

    if (!(element instanceof JsonObject)) {
      // yes, `null` is also not expected
      LOG.warn("Expected JsonObject but got " + element);
      return map;
    }

    JsonObject jsonObject = (JsonObject)element;

    if (map != null) {
      if (jsonObject.isEmpty()) {
        return map;
      }
      else if (ClassUtil.isMutableMap(map)) {
        map.clear();
      }
      else {
        map = null;
      }
    }

    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
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
      map.put(entry.getKey(), keyOrValueFromJson(entry.getValue(), valueBinding));
    }
    return map;
  }

  @Override
  public @Nullable Element serialize(@NotNull Object bean, @Nullable SerializationFilter filter) {
    throw new IllegalStateException("Do not use MapBinding as a root bean");
  }

  @Override
  public void serialize(@NotNull Object bean, @NotNull Element parent, @Nullable SerializationFilter filter) {
    Element serialized;
    if (isSurroundWithTag()) {
      serialized = new Element(MAP);
      parent.addContent(serialized);
    }
    else {
      serialized = parent;
    }
    assert serialized != null;

    @SuppressWarnings("rawtypes")
    Map map = (Map)bean;
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
  }

  private boolean isSurroundWithTag() {
    return annotation == null && (oldAnnotation == null || oldAnnotation.surroundWithTag());
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
  public @Nullable <T> Object deserializeList(@Nullable Object currentValue, @NotNull List<? extends T> elements, @NotNull DomAdapter<T> adapter) {
    List<T> childNodes;
    if (isSurroundWithTag()) {
      assert elements.size() == 1;
      childNodes = adapter.getChildren(elements.get(0));
    }
    else {
      //noinspection unchecked
      childNodes = (List<T>)elements;
    }

    return deserializeMap(currentValue, childNodes, adapter);
  }

  @Override
  public @Nullable <T> Object deserializeUnsafe(@Nullable Object context, @NotNull T element, @NotNull DomAdapter<T> adapter) {
    return null;
  }

  public @Nullable Object deserialize(@Nullable Object context, @NotNull Element element) {
    if (isSurroundWithTag()) {
      return deserializeMap(context, element.getChildren(), JdomAdapter.INSTANCE);
    }
    else {
      return deserializeMap(context, Collections.singletonList(element), JdomAdapter.INSTANCE);
    }
  }

  private <T> @Nullable Map<?, ?> deserializeMap(@Nullable Object currentValue, @NotNull List<T> childNodes, @NotNull DomAdapter<T> adapter) {
    Map map = (Map<?, ?>)currentValue;
    if (map != null) {
      if (childNodes.isEmpty()) {
        return map;
      }
      else if (ClassUtil.isMutableMap(map)) {
        map.clear();
      }
      else {
        map = null;
      }
    }

    for (T childNode : childNodes) {
      if (!adapter.getName(childNode).equals(getEntryElementName())) {
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

      if (adapter == JdomAdapter.INSTANCE) {
        //noinspection unchecked
        map.put(deserializeKeyOrValue((Element)childNode, getKeyAttributeName(), currentValue, keyBinding, keyClass),
                deserializeKeyOrValue((Element)childNode, getValueAttributeName(), currentValue, valueBinding, valueClass));
      }
      else {
        //noinspection unchecked
        map.put(deserializeKeyOrValue((XmlElement)childNode, getKeyAttributeName(), currentValue, keyBinding, keyClass),
                deserializeKeyOrValue((XmlElement)childNode, getValueAttributeName(), currentValue, valueBinding, valueClass));
      }
    }
    return map;
  }

  private void serializeKeyOrValue(@NotNull Element entry, @NotNull String attributeName, @Nullable Object value, @Nullable Binding binding, @Nullable SerializationFilter filter) {
    if (value == null) {
      return;
    }

    if (binding == null) {
      entry.setAttribute(attributeName, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)));
    }
    else {
      Element container;
      if (isSurroundKey()) {
        container = new Element(attributeName);
        entry.addContent(container);
      }
      else {
        container = entry;
      }
      binding.serialize(value, container, filter);
    }
  }

  private static @Nullable JsonElement keyOrValueToJson(@Nullable Object value, @Nullable Binding binding, @Nullable SerializationFilter filter) {
    if (value == null) {
      return null;
    }

    if (binding == null) {
      return primitiveToJsonElement(value);
    }
    else {
      return binding.toJson(value, filter);
    }
  }

  private static @Nullable Object keyOrValueFromJson(@NotNull JsonElement element, @Nullable Binding binding) {
    if (binding == null) {
      return fromJsonPrimitive(element);
    }
    else {
      return ((RootBinding)binding).fromJson(null, element);
    }
  }

  private Object deserializeKeyOrValue(@NotNull Element entry, @NotNull String attributeName, Object context, @Nullable Binding binding, @NotNull Class<?> valueClass) {
    Attribute attribute = entry.getAttribute(attributeName);
    if (attribute != null) {
      return XmlSerializerImpl.convert(attribute.getValue(), valueClass);
    }
    else if (!isSurroundKey()) {
      assert binding != null;
      for (Element element : entry.getChildren()) {
        if (binding.isBoundTo(element, JdomAdapter.INSTANCE)) {
          return binding.deserializeUnsafe(context, element, JdomAdapter.INSTANCE);
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
        return TagBindingKt.deserializeList(binding, null, children, JdomAdapter.INSTANCE);
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
        if (binding.isBoundTo(element, XmlDomAdapter.INSTANCE)) {
          return binding.deserializeUnsafe(context, element, XmlDomAdapter.INSTANCE);
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
        return TagBindingKt.deserializeList(binding, null, children, XmlDomAdapter.INSTANCE);
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
  public <T> boolean isBoundTo(@NotNull T element, @NotNull DomAdapter<T> adapter) {
    if (oldAnnotation != null && !oldAnnotation.surroundWithTag()) {
      return oldAnnotation.entryTagName().equals(adapter.getName(element));
    }
    else if (annotation != null) {
      return annotation.propertyElementName().equals(adapter.getName(element));
    }
    else {
      return adapter.getName(element).equals(MAP);
    }
  }
}
