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
package com.intellij.openapi.options.binding;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;

/**
 * @author Dmitry Avdeev
*/
public abstract class BeanValueAccessor extends ValueAccessor {

  public static BeanValueAccessor createAccessor(Object bean, final String propertyName) {
    Field[] fields = bean.getClass().getFields();
    for (final Field field : fields) {
      if (field.getName().equals(propertyName)) {
        return new BeanValueAccessor(bean, propertyName) {
          protected Object doGetValue() throws IllegalAccessException {
            return field.get(myBean);
          }

          protected void doSetValue(Object value) throws IllegalAccessException {
            field.set(myBean, value);
          }

          @Override
          public Class getType() {
            return field.getType();
          }
        };
      }
    }
    try {
      BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
      for (final PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
        if (descriptor.getName().equals(propertyName)) {
          return new BeanValueAccessor(bean, propertyName) {
            @Override
            protected Object doGetValue() throws Exception {
              return descriptor.getReadMethod().invoke(myBean);
            }

            @Override
            protected void doSetValue(Object value) throws Exception {
              descriptor.getWriteMethod().invoke(myBean, value);
            }

            @Override
            public Class getType() {
              return descriptor.getPropertyType();
            }
          };
        }
      }
      throw new IllegalArgumentException("Property " + propertyName + " not found in " + bean.getClass());
    }
    catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
  }

  protected final Object myBean;
  protected final String myPropertyName;

  public BeanValueAccessor(Object bean, String propertyName) {
    myBean = bean;
    myPropertyName = propertyName;
  }

  public Object getValue() {
    try {
      return doGetValue();
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot access property " + myPropertyName, e);
    }
  }

  public void setValue(Object value) {
    try {
      doSetValue(value);
    }
    catch (Exception e) {
      throw new RuntimeException("Cannot access property " + myPropertyName, e);
    }
  }

  protected abstract Object doGetValue() throws Exception;

  protected abstract void doSetValue(Object value) throws Exception;
}
