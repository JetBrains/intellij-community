// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

final class FieldAccessor implements MutableAccessor {
  private final Field myField;

  FieldAccessor(@NotNull Field field) {
    myField = field;
    field.setAccessible(true);
  }

  @Override
  public Object read(@NotNull Object o) {
    assert myField.getDeclaringClass().isInstance(o) : "Wrong class: " + o.getClass() + "; should be: " + myField.getDeclaringClass();
    try {
      return myField.get(o);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Reading " + myField, e);
    }
  }

  @Override
  public Object readUnsafe(@NotNull Object o) throws IllegalAccessException {
    return myField.get(o);
  }

  @Override
  public int readInt(@NotNull Object o) throws IllegalAccessException {
    return myField.getInt(o);
  }

  @Override
  public long readLong(@NotNull Object o) throws IllegalAccessException {
    return myField.getLong(o);
  }

  @Override
  public float readFloat(@NotNull Object o) throws IllegalAccessException {
    return myField.getFloat(o);
  }

  @Override
  public double readDouble(@NotNull Object o) throws IllegalAccessException {
    return myField.getDouble(o);
  }

  @Override
  public boolean readBoolean(@NotNull Object o) throws IllegalAccessException {
    return myField.getBoolean(o);
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    try {
      myField.set(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setBoolean(@NotNull Object host, boolean value) {
    try {
      myField.setBoolean(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setInt(@NotNull Object host, int value) {
    try {
      myField.setInt(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setShort(@NotNull Object host, short value) {
    try {
      myField.setShort(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setLong(@NotNull Object host, long value) {
    try {
      myField.setLong(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setFloat(@NotNull Object host, float value) {
    try {
      myField.setFloat(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setDouble(@NotNull Object host, double value) {
    try {
      myField.setDouble(host, value);
    }
    catch (IllegalAccessException e) {
      throw new SerializationException("Writing " + myField, e);
    }
  }

  @Override
  public <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass) {
    return myField.getAnnotation(annotationClass);
  }

  @NotNull
  @Override
  public String getName() {
    return myField.getName();
  }

  @NotNull
  @Override
  public Class<?> getValueClass() {
    return myField.getType();
  }

  @NotNull
  @Override
  public Type getGenericType() {
    return myField.getGenericType();
  }

  @Override
  public boolean isWritable() {
    return myField.isAccessible();
  }

  @NotNull
  public String toString() {
    return "FieldAccessor(name=" + myField.getName() + ", class=" + myField.getDeclaringClass().getName() + ")";
  }
}
