// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

final class FieldAccessor implements MutableAccessor {
  private final Field field;

  FieldAccessor(@NotNull Field field) {
    this.field = field;
    field.setAccessible(true);
  }

  @Override
  public Object read(@NotNull Object o) {
    assert field.getDeclaringClass().isInstance(o) : "Wrong class: " + o.getClass() + "; should be: " + field.getDeclaringClass();
    try {
      return field.get(o);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Reading " + field, e);
    }
  }

  @Override
  public Object readUnsafe(@NotNull Object o) throws IllegalAccessException {
    return field.get(o);
  }

  @Override
  public int readInt(@NotNull Object o) throws IllegalAccessException {
    return field.getInt(o);
  }

  @Override
  public long readLong(@NotNull Object o) throws IllegalAccessException {
    return field.getLong(o);
  }

  @Override
  public float readFloat(@NotNull Object o) throws IllegalAccessException {
    return field.getFloat(o);
  }

  @Override
  public double readDouble(@NotNull Object o) throws IllegalAccessException {
    return field.getDouble(o);
  }

  @Override
  public boolean readBoolean(@NotNull Object o) throws IllegalAccessException {
    return field.getBoolean(o);
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    try {
      field.set(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public void setBoolean(@NotNull Object host, boolean value) {
    try {
      field.setBoolean(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public void setInt(@NotNull Object host, int value) {
    try {
      field.setInt(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public void setShort(@NotNull Object host, short value) {
    try {
      field.setShort(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public void setLong(@NotNull Object host, long value) {
    try {
      field.setLong(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public void setFloat(@NotNull Object host, float value) {
    try {
      field.setFloat(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public void setDouble(@NotNull Object host, double value) {
    try {
      field.setDouble(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + field, e);
    }
  }

  @Override
  public <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass) {
    return field.getAnnotation(annotationClass);
  }

  @Override
  public <T extends Annotation> boolean isAnnotationPresent(@NotNull Class<T> annotationClass) {
    return field.isAnnotationPresent(annotationClass);
  }

  @Override
  public @NotNull String getName() {
    return field.getName();
  }

  @Override
  public @NotNull Class<?> getValueClass() {
    return field.getType();
  }

  @Override
  public @NotNull Type getGenericType() {
    return field.getGenericType();
  }

  // to support plain data classes, here we don't check that field is final
  @Override
  public boolean isWritable() {
    return field.isAccessible();
  }

  @Override
  public @NotNull String toString() {
    return "FieldAccessor(name=" + field.getName() + ", class=" + field.getDeclaringClass().getName() + ")";
  }
}
