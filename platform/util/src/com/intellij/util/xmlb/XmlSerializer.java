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

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;

public class XmlSerializer {
  private static final SerializationFilter TRUE_FILTER = new SerializationFilter() {
    @Override
    public boolean accepts(Accessor accessor, Object bean) {
      return true;
    }
  };

  private XmlSerializer() {
  }

  /**
   * Consider to use {@link SkipDefaultValuesSerializationFilters}
   */
  public static Element serialize(@NotNull Object object) throws XmlSerializationException {
    return serialize(object, TRUE_FILTER);
  }

  public static Element serialize(@NotNull Object object, @Nullable SerializationFilter filter) throws XmlSerializationException {
    return new XmlSerializerImpl(filter == null ? TRUE_FILTER : filter).serialize(object);
  }

  @Nullable
  public static <T> T deserialize(Document document, Class<T> aClass) throws XmlSerializationException {
    return deserialize(document.getRootElement(), aClass);
  }

  @Nullable
  @SuppressWarnings({"unchecked"})
  public static <T> T deserialize(Element element, Class<T> aClass) throws XmlSerializationException {
    try {
      return (T)XmlSerializerImpl.getBinding(aClass).deserialize(null, element);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  public static <T> T[] deserialize(Element[] elements, Class<T> aClass) throws XmlSerializationException {
    //noinspection unchecked
    T[] result = (T[])Array.newInstance(aClass, elements.length);

    for (int i = 0; i < result.length; i++) {
      result[i] = deserialize(elements[i], aClass);
    }

    return result;
  }

  @Nullable
  public static <T> T deserialize(URL url, Class<T> aClass) throws XmlSerializationException {
    try {
      Document document = JDOMUtil.loadDocument(url);
      document = JDOMXIncluder.resolve(document, url.toExternalForm());
      return deserialize(document.getRootElement(), aClass);
    }
    catch (IOException e) {
      throw new XmlSerializationException(e);
    }
    catch (JDOMException e) {
      throw new XmlSerializationException(e);
    }
  }

  public static void deserializeInto(@NotNull Object bean, @NotNull Element element) {
    try {
      final Binding binding = XmlSerializerImpl.getBinding(bean.getClass());
      assert binding instanceof BeanBinding;

      ((BeanBinding)binding).deserializeInto(bean, element);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  public static void serializeInto(final Object bean, final Element element) {
    serializeInto(bean, element, null);
  }

  public static void serializeInto(final Object bean, final Element element, @Nullable SerializationFilter filter) {
    if (filter == null) {
      filter = TRUE_FILTER;
    }
    try {
      final Binding binding = XmlSerializerImpl.getBinding(bean.getClass());
      assert binding instanceof BeanBinding;

      ((BeanBinding)binding).serializeInto(bean, element, filter);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }
}
