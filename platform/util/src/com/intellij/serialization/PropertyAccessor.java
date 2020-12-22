// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ExceptionUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

@ApiStatus.Internal
public final class PropertyAccessor implements MutableAccessor {
  private static final Logger LOG = Logger.getInstance(PropertyAccessor.class);

  private final String myName;
  private final Class<?> myType;
  private final Method myReadMethod;
  private final Method myWriteMethod;
  private final Type myGenericType;

  PropertyAccessor(@NotNull String name, @NotNull Class<?> type, @NotNull Method readMethod, @Nullable Method writeMethod) {
    myName = name;
    myType = type;
    myReadMethod = readMethod;
    myWriteMethod = writeMethod;
    myGenericType = myReadMethod.getGenericReturnType();

    try {
      myReadMethod.setAccessible(true);
      if (myWriteMethod != null) {
        myWriteMethod.setAccessible(true);
      }
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
      throw new SerializationException(e);
    }
    catch (InvocationTargetException e) {
      ExceptionUtilRt.rethrowUnchecked(e.getTargetException());
      throw new SerializationException(e);
    }
  }

  @Override
  public Object readUnsafe(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return myReadMethod.invoke(o);
  }

  @Override
  public int readInt(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (int)myReadMethod.invoke(o);
  }

  @Override
  public long readLong(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (long)myReadMethod.invoke(o);
  }

  @Override
  public float readFloat(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (float)myReadMethod.invoke(o);
  }

  @Override
  public double readDouble(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (double)myReadMethod.invoke(o);
  }

  @Override
  public boolean readBoolean(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (boolean)myReadMethod.invoke(o);
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    if (myWriteMethod == null) {
      throw new SerializationException(host.getClass().getName() + "::" + myName + " lacks a setter");
    }
    try {
      myWriteMethod.invoke(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException(e);
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      // see KotlinXmlSerializerTest.nullInMap
      if (cause instanceof NullPointerException &&
          myGenericType instanceof Class &&
          ((Class<?>)myGenericType).isEnum() &&
          cause.getMessage().contains("Parameter specified as non-null is null:")) {
        Object[] constants = ((Class<?>)myGenericType).getEnumConstants();
        if (constants.length > 0) {
          try {
            LOG.warn("Cannot set enum value, will be set to first enum value", e);
            myWriteMethod.invoke(host, constants[0]);
            return;
          }
          catch (IllegalAccessException | InvocationTargetException e1) {
            throw new SerializationException(e);
          }
        }
      }

      throw new SerializationException(cause);
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
    if (annotation == null && myWriteMethod != null) {
      annotation = myWriteMethod.getAnnotation(annotationClass);
    }
    return annotation;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public Class<?> getValueClass() {
    return myType;
  }

  @NotNull
  @Override
  public Type getGenericType() {
    return myGenericType;
  }

  @Override
  public boolean isWritable() {
    return myWriteMethod != null && myWriteMethod.isAccessible();
  }

  @NonNls
  public String toString() {
    return "PropertyAccessor(name=" + myName + ", class=" + myReadMethod.getDeclaringClass().getName() + ")";
  }
}