/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

class PropertyAccessor implements Accessor {
  private final String myName;
  private final Class<?> myType;
  private final Method myReadMethod;
  private final Method myWriteMethod;

  public PropertyAccessor(PropertyDescriptor descriptor) {
    this(descriptor.getName(), descriptor.getPropertyType(), descriptor.getReadMethod(), descriptor.getWriteMethod());
  }

  public PropertyAccessor(String name, Class<?> type, Method readMethod, Method writeMethod) {
    myName = name;
    myType = type;
    myReadMethod = readMethod;
    myWriteMethod = writeMethod;
  }

  public Object read(Object o) {
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

  public void write(Object o, Object value) {
    try {
      myWriteMethod.invoke(o, XmlSerializerImpl.convert(value, myType));
    }
    catch (IllegalAccessException e) {
      throw new XmlSerializationException(e);
    }
    catch (InvocationTargetException e) {
      throw new XmlSerializationException(e);
    }
  }

  public Annotation[] getAnnotations() {
    List<Annotation> result = new ArrayList<Annotation>();

    if (myReadMethod != null) {
      ContainerUtil.addAll(result, myReadMethod.getAnnotations());
    }

    if (myWriteMethod != null) {
      ContainerUtil.addAll(result, myWriteMethod.getAnnotations());
    }

    return result.toArray(new Annotation[result.size()]);
  }

  public String getName() {
    return myName;
  }

  public Class<?> getValueClass() {
    return myType;
  }

  public Type getGenericType() {
    return myReadMethod.getGenericReturnType();
  }

  @NonNls
  public String toString() {
    return "PropertyAccessor[" + myReadMethod.getDeclaringClass().getName() + "." + getName() +"]";
  }
}
