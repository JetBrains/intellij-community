// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private final String name;
  private final Class<?> type;
  private final Method readMethod;
  private final Method writeMethod;
  private final Type genericType;

  PropertyAccessor(@NotNull String name, @NotNull Class<?> type, @NotNull Method readMethod, @Nullable Method writeMethod) {
    this.name = name;
    this.type = type;
    this.readMethod = readMethod;
    this.writeMethod = writeMethod;
    genericType = readMethod.getGenericReturnType();

    try {
      this.readMethod.setAccessible(true);
      if (writeMethod != null) {
        writeMethod.setAccessible(true);
      }
    }
    catch (SecurityException ignored) {
    }
  }

  public @NotNull String getGetterName() {
    return readMethod.getName();
  }

  @Override
  public Object read(@NotNull Object o) {
    try {
      return readMethod.invoke(o);
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
    return readMethod.invoke(o);
  }

  @Override
  public int readInt(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (int)readMethod.invoke(o);
  }

  @Override
  public long readLong(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (long)readMethod.invoke(o);
  }

  @Override
  public float readFloat(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (float)readMethod.invoke(o);
  }

  @Override
  public double readDouble(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (double)readMethod.invoke(o);
  }

  @Override
  public boolean readBoolean(@NotNull Object o) throws IllegalAccessException, InvocationTargetException {
    return (boolean)readMethod.invoke(o);
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    if (writeMethod == null) {
      throw new SerializationException(host.getClass().getName() + "::" + name + " lacks a setter");
    }
    try {
      writeMethod.invoke(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException(e);
    }
    catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      // see KotlinXmlSerializerTest.nullInMap
      if (cause instanceof NullPointerException &&
          genericType instanceof Class &&
          ((Class<?>)genericType).isEnum() &&
          cause.getMessage().contains("Parameter specified as non-null is null:")) {
        Object[] constants = ((Class<?>)genericType).getEnumConstants();
        if (constants.length > 0) {
          try {
            LOG.warn("Cannot set enum value, will be set to first enum value", e);
            writeMethod.invoke(host, constants[0]);
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
    T annotation = readMethod.getAnnotation(annotationClass);
    return annotation != null || writeMethod == null ? annotation : writeMethod.getAnnotation(annotationClass);
  }

  @Override
  public <T extends Annotation> boolean isAnnotationPresent(@NotNull Class<T> annotationClass) {
    boolean isPresent = readMethod.isAnnotationPresent(annotationClass);
    return isPresent || writeMethod == null ? isPresent : writeMethod.isAnnotationPresent(annotationClass);
  }

  @Override
  public @NotNull String getName() {
    return name;
  }

  @Override
  public @NotNull Class<?> getValueClass() {
    return type;
  }

  @Override
  public @NotNull Type getGenericType() {
    return genericType;
  }

  @Override
  public boolean isWritable() {
    return writeMethod != null && writeMethod.isAccessible();
  }

  @Override
  public @NonNls String toString() {
    return "PropertyAccessor(name=" + name + ", class=" + readMethod.getDeclaringClass().getName() + ")";
  }
}