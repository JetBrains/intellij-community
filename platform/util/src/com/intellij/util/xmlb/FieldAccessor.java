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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

class FieldAccessor implements MutableAccessor {
  private final Field myField;

  public FieldAccessor(@NotNull Field field) {
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
      throw new XmlSerializationException("Reading " + myField, e);
    }
  }

  @Override
  public void set(@NotNull Object host, @Nullable Object value) {
    try {
      myField.set(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setBoolean(@NotNull Object host, boolean value) {
    try {
      myField.setBoolean(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setInt(@NotNull Object host, int value) {
    try {
      myField.setInt(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setShort(@NotNull Object host, short value) {
    try {
      myField.setShort(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setLong(@NotNull Object host, long value) {
    try {
      myField.setLong(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setFloat(@NotNull Object host, float value) {
    try {
      myField.setFloat(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public void setDouble(@NotNull Object host, double value) {
    try {
      myField.setDouble(host, value);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing " + myField, e);
    }
  }

  @Override
  public <T extends Annotation> T getAnnotation(@NotNull Class<T> annotationClass) {
    return myField.getAnnotation(annotationClass);
  }

  @Override
  public String getName() {
    return myField.getName();
  }

  @Override
  public Class<?> getValueClass() {
    return myField.getType();
  }

  @Override
  public Type getGenericType() {
    return myField.getGenericType();
  }

  @Override
  public boolean isFinal() {
    return Modifier.isFinal(myField.getModifiers());
  }

  @Override
  public void write(Object o, Object value) {
    set(o, value);
  }

  @NonNls
  public String toString() {
    return "FieldAccessor[" + myField.getDeclaringClass() + "." + myField.getName() + "]";
  }
}
