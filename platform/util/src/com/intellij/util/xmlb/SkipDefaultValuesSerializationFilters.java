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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SkipDefaultValuesSerializationFilters implements SerializationFilter {
  private final Map<Class<?>, Object> myDefaultBeans = new THashMap<Class<?>, Object>();

  @Override
  public boolean accepts(final Accessor accessor, @Nullable Object bean) {
    if (bean == null) {
      return true;
    }
    Object defaultBean = getDefaultBean(bean);

    final Object defValue = accessor.read(defaultBean);
    final Object beanValue = accessor.read(bean);
    if (defValue instanceof Element && beanValue instanceof Element) {
      return !JDOMUtil.areElementsEqual((Element)beanValue, (Element)defValue);
    }
    else {
      return !Comparing.equal(beanValue, defValue);
    }
  }

  private Object getDefaultBean(@NotNull Object bean) {
    Class<?> c = bean.getClass();
    Object o = myDefaultBeans.get(c);
    if (o == null) {
      o = XmlSerializerImpl.newInstance(c);
      configure(o);

      myDefaultBeans.put(c, o);
    }
    return o;
  }

  /**
   * Override to put your own default object configuration
   */
  protected void configure(@NotNull Object o) {
  }
}
