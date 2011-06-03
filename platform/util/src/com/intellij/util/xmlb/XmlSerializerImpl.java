/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import org.jdom.*;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author mike
 */
class XmlSerializerImpl {
  private final SerializationFilter filter;
  private final Map<Pair<Type, Accessor>, Binding> myBindings = new HashMap<Pair<Type, Accessor>, Binding>();

  public XmlSerializerImpl(SerializationFilter filter) {
    this.filter = filter;
  }

  Element serialize(Object object) throws XmlSerializationException {
    try {
      return (Element)getBinding(object.getClass()).serialize(object, null);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  Binding getBinding(Type type) {
    return getTypeBinding(type, null);
  }

  Binding getBinding(Accessor accessor) {
    return getTypeBinding(accessor.getGenericType(), accessor);
  }

  Binding getTypeBinding(Type type, @Nullable Accessor accessor) {
    if (type instanceof Class) {
      return _getClassBinding((Class<?>)type, type, accessor);
    }
    else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      Type rawType = parameterizedType.getRawType();
      assert rawType instanceof Class;
      return _getClassBinding((Class<?>)rawType, type, accessor);
    }

    throw new UnsupportedOperationException("Can't get binding for: " + type);
  }

  private Binding _getClassBinding(Class<?> aClass, Type originalType, final Accessor accessor) {
    final Pair<Type, Accessor> p = new Pair<Type, Accessor>(originalType, accessor);

    Binding binding = myBindings.get(p);

    if (binding == null) {
      binding = _getNonCachedClassBinding(aClass, accessor, originalType);
      myBindings.put(p, binding);
      binding.init();
    }

    return binding;
  }

  private Binding _getNonCachedClassBinding(final Class<?> aClass, final Accessor accessor, final Type originalType) {
    if (aClass.isPrimitive()) return new PrimitiveValueBinding(aClass);
    if (aClass.isArray()) {
      return Element.class.isAssignableFrom(aClass.getComponentType())
             ? new JDOMElementBinding(accessor) : new ArrayBinding(this, aClass, accessor);
    }
    if (Number.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (Boolean.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (String.class.isAssignableFrom(aClass)) return new PrimitiveValueBinding(aClass);
    if (Collection.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
      return new CollectionBinding((ParameterizedType)originalType, this, accessor);
    }
    if (Map.class.isAssignableFrom(aClass) && originalType instanceof ParameterizedType) {
      return new MapBinding((ParameterizedType)originalType, this, accessor);
    }
    if (Element.class.isAssignableFrom(aClass)) return new JDOMElementBinding(accessor);
    if (Date.class.isAssignableFrom(aClass)) return new DateBinding();
    if (aClass.isEnum()) return new PrimitiveValueBinding(aClass);

    return new BeanBinding(aClass, this, accessor);
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
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
    if (value == null) return null;
    if (type.isInstance(value)) return (T)value;
    if (String.class.isAssignableFrom(type)) return (T)String.valueOf(value);
    if (int.class.isAssignableFrom(type) || Integer.class.isAssignableFrom(type)) return (T)Integer.valueOf(String.valueOf(value));
    if (double.class.isAssignableFrom(type) || Double.class.isAssignableFrom(type)) return (T)Double.valueOf(String.valueOf(value));
    if (float.class.isAssignableFrom(type) || Float.class.isAssignableFrom(type)) return (T)Float.valueOf(String.valueOf(value));
    if (long.class.isAssignableFrom(type) || Long.class.isAssignableFrom(type)) return (T)Long.valueOf(String.valueOf(value));
    if (boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type)) return (T)Boolean.valueOf(String.valueOf(value));

    if (type.isEnum()) {
      final T[] enumConstants = type.getEnumConstants();
      for (T enumConstant : enumConstants) {
        if (enumConstant.toString().equals(value.toString())) return enumConstant;
      }

      return null;
    }

    throw new XmlSerializationException("Can't covert " + value.getClass() + " into " + type);
  }

  public SerializationFilter getFilter() {
    return filter;
  }

  public static boolean isIgnoredNode(final Object child) {
    if (child instanceof Text && ((Text)child).getValue().trim().length() == 0) {
      return true;
    }
    if (child instanceof Comment) {
      return true;
    }
    if (child instanceof Attribute) {
      Attribute attr = (Attribute)child;
      final String namespaceURI = attr.getNamespaceURI();
      if (namespaceURI != null && namespaceURI != "") return true;
    }

    return false;
  }

  public static Content[] getNotIgnoredContent(final Element m) {
    List<Content> result = new ArrayList<Content>();
    final List content = m.getContent();

    for (Object o : content) {
      if (!isIgnoredNode(o)) result.add((Content)o);
    }

    return result.toArray(new Content[result.size()]);
  }
}
