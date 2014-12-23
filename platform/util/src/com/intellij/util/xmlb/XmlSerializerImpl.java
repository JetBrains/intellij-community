/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.*;
import org.jdom.filter.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.ref.SoftReference;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mike
 */
class XmlSerializerImpl {
  private static final Filter<Content> CONTENT_FILTER = new Filter<Content>() {
    @Override
    public boolean matches(Object obj) {
      return !isIgnoredNode(obj);
    }
  };

  private static SoftReference<Map<Pair<Type, Accessor>, Binding>> ourBindings;

  @NotNull
  static List<Content> getFilteredContent(@NotNull Element element) {
    List<Content> content = element.getContent();
    if (content.isEmpty()) {
      return content;
    }
    else if (content.size() == 1) {
      return isIgnoredNode(content.get(0)) ? Collections.<Content>emptyList() : content;
    }
    else {
      return element.getContent(CONTENT_FILTER);
    }
  }

  @NotNull
  static Element serialize(@NotNull Object object, @NotNull SerializationFilter filter) throws XmlSerializationException {
    try {
      Class<?> aClass = object.getClass();
      Binding binding = _getClassBinding(aClass, aClass, null);
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

  @Nullable
  static Element serializeIfNotDefault(@NotNull Object object, @NotNull SerializationFilter filter) {
    Class<?> aClass = object.getClass();
    return (Element)_getClassBinding(aClass, aClass, null).serialize(object, null, filter);
  }

  static Binding getBinding(@NotNull Type type) {
    return getTypeBinding(type, null);
  }

  static Binding getBinding(@NotNull Accessor accessor) {
    return getTypeBinding(accessor.getGenericType(), accessor);
  }

  static Binding getTypeBinding(@NotNull Type type, @Nullable Accessor accessor) {
    Class<?> aClass;
    if (type instanceof Class) {
      aClass = (Class<?>)type;
    }
    else if (type instanceof TypeVariable) {
      Type bound = ((TypeVariable)type).getBounds()[0];
      aClass = bound instanceof Class ? (Class)bound : (Class<?>)((ParameterizedType)bound).getRawType();
    }
    else {
      aClass = (Class<?>)((ParameterizedType)type).getRawType();
    }
    return _getClassBinding(aClass, type, accessor);
  }

  @NotNull
  private static synchronized Binding _getClassBinding(@NotNull Class<?> aClass, @NotNull Type originalType, @Nullable Accessor accessor) {
    Pair<Type, Accessor> key = Pair.create(originalType, accessor);
    Map<Pair<Type, Accessor>, Binding> map = getBindingCacheMap();
    Binding binding = map.get(key);
    if (binding == null) {
      binding = _getNonCachedClassBinding(aClass, accessor, originalType);
      map.put(key, binding);
      try {
        binding.init();
      } catch (XmlSerializationException e) {
        map.remove(key);
        throw e;
      }
    }
    return binding;
  }

  @NotNull
  private static Map<Pair<Type, Accessor>, Binding> getBindingCacheMap() {
    Map<Pair<Type, Accessor>, Binding> map = com.intellij.reference.SoftReference.dereference(ourBindings);
    if (map == null) {
      map = new ConcurrentHashMap<Pair<Type, Accessor>, Binding>();
      ourBindings = new SoftReference<Map<Pair<Type, Accessor>, Binding>>(map);
    }
    return map;
  }

  @NotNull
  private static Binding _getNonCachedClassBinding(@NotNull Class<?> aClass, @Nullable Accessor accessor, @NotNull Type originalType) {
    if (aClass.isPrimitive()) {
      return new PrimitiveValueBinding(aClass, accessor);
    }
    if (aClass.isArray()) {
      return Element.class.isAssignableFrom(aClass.getComponentType())
             ? new JDOMElementBinding(accessor) : new ArrayBinding(aClass, accessor);
    }
    if (Number.class.isAssignableFrom(aClass)) {
      return new PrimitiveValueBinding(aClass, accessor);
    }
    if (Boolean.class.isAssignableFrom(aClass)) {
      return new PrimitiveValueBinding(aClass, accessor);
    }
    if (String.class.isAssignableFrom(aClass)) {
      return new PrimitiveValueBinding(aClass, accessor);
    }
    if (Collection.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
      return new CollectionBinding((ParameterizedType)originalType, accessor);
    }
    if (accessor != null) {
      if (Map.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
        return new MapBinding((ParameterizedType)originalType, accessor);
      }
      if (Element.class.isAssignableFrom(aClass)) {
        return new JDOMElementBinding(accessor);
      }
      //noinspection deprecation
      if (JDOMExternalizableStringList.class == aClass) {
        return new JDOMExternalizableStringListBinding(accessor);
      }
    }
    if (Date.class.isAssignableFrom(aClass)) {
      return new DateBinding(accessor);
    }
    if (aClass.isEnum()) {
      return new PrimitiveValueBinding(aClass, accessor);
    }
    return new BeanBinding(aClass, accessor);
  }

  @Nullable
  @Deprecated
  @SuppressWarnings({"unchecked", "unused"})
  /**
   * @deprecated to remove in IDEA 15
   */
  static <T> T findAnnotation(Annotation[] annotations, Class<T> aClass) {
    if (annotations == null) return null;

    for (Annotation annotation : annotations) {
      if (aClass.isAssignableFrom(annotation.getClass())) return (T)annotation;
    }
    return null;
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  static <T> T convert(Object value, Class<T> type) {
    if (value == null) {
      return null;
    }
    if (type.isInstance(value)) {
      return (T)value;
    }
    if (String.class.isAssignableFrom(type)) {
      return (T)String.valueOf(value);
    }
    if (int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)) {
      return (T)Integer.valueOf(String.valueOf(value));
    }
    if (double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)) {
      return (T)Double.valueOf(String.valueOf(value));
    }
    if (float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)) {
      return (T)Float.valueOf(String.valueOf(value));
    }
    if (long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)) {
      return (T)Long.valueOf(String.valueOf(value));
    }
    if (boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)) {
      return (T)Boolean.valueOf(String.valueOf(value));
    }
    if (char.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type)) {
      return (T)value;
    }
    if (type.isEnum()) {
      final T[] enumConstants = type.getEnumConstants();
      for (T enumConstant : enumConstants) {
        if (enumConstant.toString().equals(value.toString())) return enumConstant;
      }
      return null;
    }

    throw new XmlSerializationException("Can't covert " + value.getClass() + " into " + type);
  }

  public static boolean isIgnoredNode(final Object child) {
    if (child instanceof Text && StringUtil.isEmptyOrSpaces(((Text)child).getValue())) {
      return true;
    }
    if (child instanceof Comment) {
      return true;
    }
    if (child instanceof Attribute) {
      if (!StringUtil.isEmpty(((Attribute)child).getNamespaceURI())) {
        return true;
      }
    }
    return false;
  }
}
