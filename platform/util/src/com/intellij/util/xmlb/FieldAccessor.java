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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

class FieldAccessor implements Accessor {
  private final Field myField;

  public FieldAccessor(@NotNull Field field) {
    myField = field;
    field.setAccessible(true);
  }

  @Override
  public Object read(Object o) {
    assert myField.getDeclaringClass().isInstance(o) : "Wrong class: " + o.getClass() + "; should be: " + myField.getDeclaringClass();
    try {
      return myField.get(o);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Reading "+myField, e);
    }
  }

  @Override
  public void write(Object o, Object value) {
    Class<?> declaringClass = myField.getDeclaringClass();
    assert declaringClass.isInstance(o) : "Wrong class: " + o.getClass() + "; should be: " + declaringClass;
    try {
      Class<?> type = myField.getType();
      myField.set(o, XmlSerializerImpl.convert(value, type));
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException("Writing "+myField,e);
    }
  }

  @NotNull
  @Override
  public Annotation[] getAnnotations() {
    return myField.getAnnotations();
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

  @NonNls
  public String toString() {
    return "FieldAccessor[" + myField.getDeclaringClass() + "." + myField.getName() + "]";
  }
}
