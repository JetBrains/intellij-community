/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.CollectionBean;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.Verifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class XmlSerializerImpl {
  public abstract static class XmlSerializerBase implements Serializer {
    @Override
    @Nullable
    public final Binding getBinding(@NotNull Type type) {
      return getBinding(typeToClass(type), type, null);
    }

    @Nullable
    protected final Binding getBinding(@NotNull Class<?> aClass, @NotNull Type originalType, @Nullable MutableAccessor accessor) {
      return isPrimitive(aClass) ? null : getClassBinding(aClass, originalType, accessor);
    }

    @Nullable
    @Override
    public final Binding getBinding(@NotNull Class<?> aClass, @NotNull Type type) {
      return getBinding(aClass, type, null);
    }

    @Nullable
    @Override
    public final Binding getBinding(@NotNull MutableAccessor accessor) {
      Type type = accessor.getGenericType();
      return getBinding(typeToClass(type), type, accessor);
    }

    @Override
    public final Binding getClassBinding(@NotNull Class<?> aClass) {
      return getClassBinding(aClass, aClass, null);
    }

    @Nullable
    protected static Binding createClassBinding(@NotNull Class<?> aClass, @Nullable MutableAccessor accessor, @NotNull Type originalType) {
      if (aClass.isArray()) {
        if (Element.class.isAssignableFrom(aClass.getComponentType())) {
          assert accessor != null;
          return new JDOMElementBinding(accessor);
        }
        else {
          return new ArrayBinding(aClass, accessor);
        }
      }
      if (Collection.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
        if (accessor != null) {
          CollectionBean listBean = accessor.getAnnotation(CollectionBean.class);
          if (listBean != null) {
            return new CompactCollectionBinding(accessor);
          }
        }
        return new CollectionBinding((ParameterizedType)originalType, accessor);
      }
      if (accessor != null) {
        if (Map.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
          return new MapBinding(accessor);
        }
        if (Element.class.isAssignableFrom(aClass)) {
          return new JDOMElementBinding(accessor);
        }
        //noinspection deprecation
        if (JDOMExternalizableStringList.class == aClass) {
          return new CompactCollectionBinding(accessor);
        }
      }
      return null;
    }
  }

  static class XmlSerializer extends XmlSerializerBase {
    private Reference<Map<Pair<Type, MutableAccessor>, Binding>> ourBindings;

    @NotNull
    private Map<Pair<Type, MutableAccessor>, Binding> getBindingCacheMap() {
      Map<Pair<Type, MutableAccessor>, Binding> map = com.intellij.reference.SoftReference.dereference(ourBindings);
      if (map == null) {
        map = ContainerUtil.newConcurrentMap();
        ourBindings = new SoftReference<Map<Pair<Type, MutableAccessor>, Binding>>(map);
      }
      return map;
    }

    @NotNull
    @Override
    public synchronized Binding getClassBinding(@NotNull Class<?> aClass, @NotNull Type originalType, @Nullable MutableAccessor accessor) {
      Pair<Type, MutableAccessor> key = Pair.create(originalType, accessor);
      Map<Pair<Type, MutableAccessor>, Binding> map = getBindingCacheMap();
      Binding binding = map.get(key);
      if (binding == null) {
        binding = createClassBinding(aClass, accessor, originalType);
        if (binding == null) {
          binding = new BeanBinding(aClass, accessor);
        }

        map.put(key, binding);
        try {
          binding.init(originalType, this);
        }
        catch (RuntimeException e) {
          map.remove(key);
          throw e;
        }
        catch (Error e) {
          map.remove(key);
          throw e;
        }
      }
      return binding;
    }
  }

  static final XmlSerializer serializer = new XmlSerializer();

  @NotNull
  static Element serialize(@NotNull Object object, @Nullable SerializationFilter filter) throws XmlSerializationException {
    try {
      Class<?> aClass = object.getClass();
      Binding binding = serializer.getClassBinding(aClass);
      if (binding instanceof BeanBinding) {
        // top level expects not null (null indicates error, empty element will be omitted)
        return ((BeanBinding)binding).serialize(object, true, filter);
      }
      else {
        //noinspection ConstantConditions
        return (Element)binding.serialize(object, null, filter);
      }
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException("Can't serialize instance of " + object.getClass(), e);
    }
  }

  @NotNull
  static Class<?> typeToClass(@NotNull Type type) {
    if (type instanceof Class) {
      return (Class<?>)type;
    }
    else if (type instanceof TypeVariable) {
      Type bound = ((TypeVariable)type).getBounds()[0];
      return bound instanceof Class ? (Class)bound : (Class<?>)((ParameterizedType)bound).getRawType();
    }
    else if (type instanceof WildcardType) {
      return (Class<?>)((WildcardType) type).getUpperBounds()[0];
    }
    else {
      return (Class<?>)((ParameterizedType)type).getRawType();
    }
  }

  static boolean isPrimitive(@NotNull Class<?> aClass) {
    return aClass.isPrimitive() ||
        aClass == String.class ||
        aClass == Integer.class ||
        aClass == Long.class ||
        aClass == Boolean.class ||
        aClass == Double.class ||
        aClass == Float.class ||
        aClass.isEnum() ||
        Date.class.isAssignableFrom(aClass);
  }

  @Nullable
  static Object convert(@Nullable String value, @NotNull Class<?> valueClass) {
    if (value == null) {
      return null;
    }
    else if (valueClass == String.class) {
      return value;
    }
    else if (valueClass == int.class || valueClass == Integer.class) {
      return Integer.parseInt(value);
    }
    else if (valueClass == boolean.class || valueClass == Boolean.class) {
      return Boolean.parseBoolean(value);
    }
    else if (valueClass == double.class || valueClass == Double.class) {
      return Double.parseDouble(value);
    }
    else if (valueClass == float.class || valueClass == Float.class) {
      return Float.parseFloat(value);
    }
    else if (valueClass == long.class || valueClass == Long.class) {
      return Long.parseLong(value);
    }
    else if (valueClass.isEnum()) {
      for (Object enumConstant : valueClass.getEnumConstants()) {
        if (enumConstant.toString().equals(value)) {
          return enumConstant;
        }
      }
      return null;
    }
    else if (Date.class.isAssignableFrom(valueClass)) {
      try {
        return new Date(Long.parseLong(value));
      }
      catch (NumberFormatException e) {
        return new Date(0);
      }
    }
    else {
      return value;
    }
  }

  static void doSet(@NotNull Object host, @Nullable String value, @NotNull MutableAccessor accessor, @NotNull Class<?> valueClass) {
    if (value == null) {
      accessor.set(host, null);
    }
    else if (valueClass == String.class) {
      accessor.set(host, value);
    }
    else if (valueClass == int.class) {
      accessor.setInt(host, Integer.parseInt(value));
    }
    else if (valueClass == boolean.class) {
      accessor.setBoolean(host, Boolean.parseBoolean(value));
    }
    else if (valueClass == double.class) {
      accessor.setDouble(host, Double.parseDouble(value));
    }
    else if (valueClass == float.class) {
      accessor.setFloat(host, Float.parseFloat(value));
    }
    else if (valueClass == long.class) {
      accessor.setLong(host, Long.parseLong(value));
    }
    else if (valueClass == short.class) {
      accessor.setShort(host, Short.parseShort(value));
    }
    else if (valueClass.isEnum()) {
      Object deserializedValue = null;
      for (Object enumConstant : valueClass.getEnumConstants()) {
        if (enumConstant.toString().equals(value)) {
          deserializedValue = enumConstant;
        }
      }
      accessor.set(host, deserializedValue);
    }
    else if (Date.class.isAssignableFrom(valueClass)) {
      try {
        accessor.set(host, new Date(Long.parseLong(value)));
      }
      catch (NumberFormatException e) {
        accessor.set(host, new Date(0));
      }
    }
    else {
      Object deserializedValue = value;
      if (valueClass == Boolean.class) {
        deserializedValue = Boolean.parseBoolean(value);
      }
      else if (valueClass == Integer.class) {
        deserializedValue = Integer.parseInt(value);
      }
      else if (valueClass == Short.class) {
        deserializedValue = Short.parseShort(value);
      }
      else if (valueClass == Long.class) {
        deserializedValue = Long.parseLong(value);
      }
      else if (valueClass == Double.class) {
        deserializedValue = Double.parseDouble(value);
      }
      else if (valueClass == Float.class) {
        deserializedValue = Float.parseFloat(value);
      }
      else if (callFromStringIfDefined(host, value, accessor, valueClass)) {
        return;
      }

      accessor.set(host, deserializedValue);
    }
  }

  private static boolean callFromStringIfDefined(@NotNull Object host,
                                                 @NotNull String value,
                                                 @NotNull MutableAccessor accessor,
                                                 @NotNull Class<?> valueClass) {
    Method fromText;
    try {
      fromText = valueClass.getMethod("fromText", String.class);
    }
    catch (NoSuchMethodException ignored) {
      return false;
    }

    try {
      fromText.setAccessible(true);
    }
    catch (SecurityException ignored) {
    }

    try {
      accessor.set(host, fromText.invoke(null, value));
      return true;
    }
    catch (IllegalAccessException ignored) {
    }
    catch (InvocationTargetException ignored) {
    }
    return false;
  }

  @NotNull
  static String convertToString(@NotNull Object value) {
    if (value instanceof Date) {
      return Long.toString(((Date)value).getTime());
    }
    else {
      return value.toString();
    }
  }

  @NotNull
  static String removeControlChars(@NotNull String text) {
    StringBuilder result = null;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!Verifier.isXMLCharacter(c)) {
        if (result == null) {
          result = new StringBuilder(text.length());
          result.append(text, 0, i);
        }
        continue;
      }

      if (result != null) {
        result.append(c);
      }
    }
    return result == null ? text : result.toString();
  }

  @NotNull
  static String getTextValue(@NotNull Element element, @NotNull String defaultText) {
    List<Content> content = element.getContent();
    int size = content.size();
    StringBuilder builder = null;
    for (int i = 0; i < size; i++) {
      Content child = content.get(i);
      if (child instanceof Text) {
        String value = child.getValue();
        if (builder == null && i == (size - 1)) {
          return value;
        }

        if (builder == null) {
          builder = new StringBuilder();
        }
        builder.append(value);
      }
    }
    return builder == null ? defaultText : builder.toString();
  }
}
