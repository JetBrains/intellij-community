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

import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Type;

class FieldAccessor implements Accessor {
  private final Field myField;

  public FieldAccessor(Field myField) {
    this.myField = myField;
  }

  public Object read(Object o) {
    assert myField.getDeclaringClass().isInstance(o) : "Wrong class: " + o.getClass() + " should be: " + myField.getDeclaringClass();
    try {
      return myField.get(o);
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
  }

  public void write(Object o, Object value) {
    assert myField.getDeclaringClass().isInstance(o) : "Wrong class: " + o.getClass() + " should be: " + myField.getDeclaringClass();
    try {
      myField.set(o, XmlSerializerImpl.convert(value, myField.getType()));
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
  }

  public Annotation[] getAnnotations() {
    return myField.getAnnotations();
  }

  public String getName() {
    return myField.getName();
  }

  public Class<?> getValueClass() {
    return myField.getType();
  }

  public Type getGenericType() {
    return myField.getGenericType();
  }

  @NonNls
  public String toString() {
    return "FieldAccessor[" + myField.getDeclaringClass() + "." + myField.getName() + "]";
  }
}
