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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

class PropertyAccessor implements Accessor {
  private final PropertyDescriptor myPropertyDescriptor;


  public PropertyAccessor(PropertyDescriptor myPropertyDescriptor) {
    this.myPropertyDescriptor = myPropertyDescriptor;
  }


  public Object read(Object o) {
    try {
      return myPropertyDescriptor.getReadMethod().invoke(o);
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
      myPropertyDescriptor.getWriteMethod().invoke(o, XmlSerializerImpl.convert(value, myPropertyDescriptor.getPropertyType()));
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

    if (myPropertyDescriptor.getReadMethod() != null) {
      ContainerUtil.addAll(result, myPropertyDescriptor.getReadMethod().getAnnotations());
    }

    if (myPropertyDescriptor.getWriteMethod() != null) {
      ContainerUtil.addAll(result, myPropertyDescriptor.getWriteMethod().getAnnotations());
    }

    return result.toArray(new Annotation[result.size()]);
  }

  public String getName() {
    return myPropertyDescriptor.getName();
  }

  public Class<?> getValueClass() {
    return myPropertyDescriptor.getPropertyType();
  }

  public Type getGenericType() {
    return myPropertyDescriptor.getReadMethod().getGenericReturnType();
  }


  @NonNls
  public String toString() {
    return "PropertyAccessor[" + myPropertyDescriptor.getReadMethod().getDeclaringClass().getName() + "." + getName() +"]";
  }
}
