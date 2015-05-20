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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

class PropertyAccessor implements MutableAccessor {
  private final String myName;
  private final Class<?> myType;
  private final Method myReadMethod;
  private final Method setter;
  private final Type myGenericType;

  public PropertyAccessor(PropertyDescriptor descriptor) {
    this(descriptor.getName(), descriptor.getPropertyType(), descriptor.getReadMethod(), descriptor.getWriteMethod());
  }

  public PropertyAccessor(String name, Class<?> type, @NotNull Method readMethod, @NotNull Method writeMethod) {
    myName = name;
    myType = type;
    myReadMethod = readMethod;
    setter = writeMethod;
    myGenericType = myReadMethod.getGenericReturnType();

    try {
      myReadMethod.setAccessible(true);
      setter.setAccessible(true);
    }
    catch (SecurityException ignored) {
    }
  }

  @Override
  public Object read(@NotNull Object o) {
    try {
      return myReadMethod.invoke(o);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
    catch (InvocationTargetException e) {
      throw new XmlSerializationException(e);
    }
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    try {
      setter.invoke(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
    catch (InvocationTargetException e) {
      throw new XmlSerializationException(e);
    }
  }

  @Override
  public void setBoolean(@NotNull Object host, boolean value) {
    set(host, value);
  }

  @Override
  public void setInt(@NotNull Object host, int value) {
    set(host, value);
  }

  @Override
  public void setShort(@NotNull Object host, short value) {
    set(host, value);
  }

  @Override
  public void setLong(@NotNull Object host, long value) {
    set(host, value);
  }

  @Override
  public void setDouble(@NotNull Object host, double value) {
    set(host, value);
  }

  @Override
  public void setFloat(@NotNull Object host, float value) {
    set(host, value);
  }

  @Override
  public <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass) {
    T annotation = myReadMethod.getAnnotation(annotationClass);
    return annotation == null ? setter.getAnnotation(annotationClass) : annotation;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public Class<?> getValueClass() {
    return myType;
  }

  @Override
  public Type getGenericType() {
    return myGenericType;
  }

  @Override
  public boolean isFinal() {
    return false;
  }

  @NonNls
  public String toString() {
    return "PropertyAccessor[" + myReadMethod.getDeclaringClass().getName() + "." + getName() +"]";
  }

  @Override
  public void write(Object o, Object value) {
    set(o, value);
  }
}
