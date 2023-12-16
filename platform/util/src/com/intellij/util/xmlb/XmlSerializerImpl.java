// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.serialization.ClassUtil;
import com.intellij.serialization.MutableAccessor;
import com.intellij.serialization.SerializationException;
import com.intellij.util.xmlb.annotations.CollectionBean;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class XmlSerializerImpl {
  public abstract static class XmlSerializerBase implements Serializer {
    @Override
    public final @Nullable Binding getBinding(@NotNull Class<?> aClass, @NotNull Type type) {
      return ClassUtil.isPrimitive(aClass) ? null : getRootBinding(aClass, type);
    }

    @Override
    public final synchronized @NotNull Binding getRootBinding(@NotNull Class<?> aClass, @NotNull Type originalType, @NotNull MutableAccessor accessor) {
      // do not cache because a client will cache it in any case
      Binding binding = createClassBinding(aClass, accessor, originalType);
      if (binding == null) {
        // BeanBinding doesn't depend on accessor, get from cache or compute
        binding = getRootBinding(aClass, originalType);
      }
      else {
        binding.init(originalType, this);
      }
      return binding;
    }

    @Override
    public final @Nullable Binding getBinding(@NotNull MutableAccessor accessor) {
      Type type = accessor.getGenericType();
      Class<?> aClass = ClassUtil.typeToClass(type);
      return ClassUtil.isPrimitive(aClass) ? null : getRootBinding(aClass, type, accessor);
    }

    protected static @Nullable Binding createClassBinding(@NotNull Class<?> aClass, @Nullable MutableAccessor accessor, @NotNull Type originalType) {
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

      if (Map.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
        //noinspection unchecked
        return new MapBinding(accessor, (Class<? extends Map<?, ?>>)aClass);
      }

      if (accessor != null) {
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

  static final class XmlSerializer extends XmlSerializerBase {
    private Reference<Map<Type, Binding>> ourBindings;

    private @NotNull Map<Type, Binding> getBindingCacheMap() {
      Map<Type, Binding> map = com.intellij.reference.SoftReference.dereference(ourBindings);
      if (map == null) {
        map = new ConcurrentHashMap<>();
        ourBindings = new SoftReference<>(map);
      }
      return map;
    }

    @Override
    public synchronized @NotNull Binding getRootBinding(@NotNull Class<?> aClass, @NotNull Type originalType) {
      Map<Type, Binding> map = getBindingCacheMap();
      Binding binding = map.get(originalType);
      if (binding == null) {
        binding = createClassBinding(aClass, null, originalType);
        if (binding == null) {
          binding = new BeanBinding(aClass);
        }

        map.put(originalType, binding);
        try {
          binding.init(originalType, this);
        }
        catch (RuntimeException | Error e) {
          map.remove(originalType);
          throw e;
        }
      }
      return binding;
    }
  }

  static final XmlSerializer serializer = new XmlSerializer();

  static @NotNull Element serialize(@NotNull Object object, @Nullable SerializationFilter filter) throws SerializationException {
    try {
      Class<?> aClass = object.getClass();
      Binding binding = serializer.getRootBinding(aClass, aClass);
      if (binding instanceof BeanBinding) {
        // top level expects not null (null indicates error, an empty element will be omitted)
        return ((BeanBinding)binding).serialize(object, true, filter);
      }
      else {
        //noinspection ConstantConditions
        return (Element)binding.serialize(object, null, filter);
      }
    }
    catch (SerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException("Can't serialize instance of " + object.getClass(), e);
    }
  }

  static @Nullable Object convert(@Nullable String value, @NotNull Class<?> valueClass) {
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
      //noinspection unchecked
      accessor.set(host, ClassUtil.stringToEnum(value, (Class<? extends Enum<?>>)valueClass, false));
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
    catch (IllegalAccessException | InvocationTargetException ignored) {
    }
    return false;
  }

  static @NotNull String convertToString(@NotNull Object value) {
    if (value instanceof Date) {
      return Long.toString(((Date)value).getTime());
    }
    else {
      return value.toString();
    }
  }

  static @NotNull String getTextValue(@NotNull Element element, @NotNull String defaultText) {
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
