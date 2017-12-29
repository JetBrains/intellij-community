/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import static com.intellij.util.xmlb.Binding.LOG;

public class PropertyAccessor implements MutableAccessor {
  private final String myName;
  private final Class<?> myType;
  private final Method myReadMethod;
  private final Method myWriteMethod;
  private final Type myGenericType;

  public PropertyAccessor(PropertyDescriptor descriptor) {
    this(descriptor.getName(), descriptor.getPropertyType(), descriptor.getReadMethod(), descriptor.getWriteMethod());
  }

  public PropertyAccessor(String name, Class<?> type, @NotNull Method readMethod, @NotNull Method writeMethod) {
    myName = name;
    myType = type;
    myReadMethod = readMethod;
    myWriteMethod = writeMethod;
    myGenericType = myReadMethod.getGenericReturnType();

    try {
      myReadMethod.setAccessible(true);
      myWriteMethod.setAccessible(true);
    }
    catch (SecurityException ignored) { }
  }

  @NotNull
  public String getGetterName() {
    return myReadMethod.getName();
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
      Throwable exception = e.getTargetException();
      ExceptionUtil.rethrowUnchecked(exception);
      throw new XmlSerializationException(e);
    }
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    try {
      myWriteMethod.invoke(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      // see KotlinXmlSerializerTest.nullInMap
      if (cause instanceof IllegalArgumentException && myGenericType instanceof Class && ((Class)myGenericType).isEnum() && cause.getMessage().contains("Parameter specified as non-null is null:")) {
        Object[] constants = ((Class)myGenericType).getEnumConstants();
        if (constants.length > 0) {
          try {
            LOG.warn("Cannot set enum value, will be set to first enum value", e);
            myWriteMethod.invoke(host, constants[0]);
            return;
          }
          catch (IllegalAccessException e1) {
            throw new XmlSerializationException(e);
          }
          catch (InvocationTargetException e1) {
            throw new XmlSerializationException(cause);
          }
        }
      }

      throw new XmlSerializationException(cause);
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
    if (annotation == null) annotation = myWriteMethod.getAnnotation(annotationClass);
    return annotation;
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
}
