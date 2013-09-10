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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;

import java.util.HashMap;
import java.util.Map;

public class SkipDefaultValuesSerializationFilters implements SerializationFilter {
  private final Map<Class, Object> myDefaultBeans = new HashMap<Class, Object>();

  @Override
  public boolean accepts(final Accessor accessor, final Object bean) {
    if (bean == null) {
      return true;
    }
    Object defaultBean = getDefaultBean(bean);

    final Object defValue = accessor.read(defaultBean);
    final Object beanValue = accessor.read(bean);
    if (defValue instanceof Element && beanValue instanceof Element) {
      return !JDOMUtil.writeElement((Element)defValue, "\n").equals(JDOMUtil.writeElement((Element)beanValue, "\n"));
    }

    return !Comparing.equal(beanValue, defValue);
  }

  private Object getDefaultBean(final Object bean) {
    Class c = bean.getClass();
    Object o = myDefaultBeans.get(c);

    if (o == null) {
      try {
        o = c.newInstance();
        configure(o);
      }
      catch (InstantiationException e) {
        throw new XmlSerializationException(e);
      }
      catch (IllegalAccessException e) {
        throw new XmlSerializationException(e);
      }

      myDefaultBeans.put(c, o);
    }

    return o;
  }

  protected void configure(final Object o) {
    //todo put your own default object configuration here
  }
}
