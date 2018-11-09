// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;

public class XmlSerializer {
  private static final SerializationFilter TRUE_FILTER = new SerializationFilter() {
    @Override
    public boolean accepts(@NotNull Accessor accessor, @NotNull Object bean) {
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

  @NotNull
  public static Element serialize(@NotNull Object object, @Nullable SerializationFilter filter) throws XmlSerializationException {
    return XmlSerializerImpl.serialize(object, filter == null ? TRUE_FILTER : filter);
  }

  @Nullable
  public static Element serializeIfNotDefault(@NotNull Object object, @Nullable SerializationFilter filter) {
    SerializationFilter filter1 = filter == null ? TRUE_FILTER : filter;
    Class<?> aClass = object.getClass();
    return (Element)XmlSerializerImpl.serializer.getClassBinding(aClass).serialize(object, null, filter1);
  }

  @NotNull
  public static <T> T deserialize(Document document, Class<T> aClass) throws XmlSerializationException {
    return deserialize(document.getRootElement(), aClass);
  }

  @NotNull
  @SuppressWarnings({"unchecked"})
  public static <T> T deserialize(@NotNull Element element, @NotNull Class<T> aClass) throws XmlSerializationException {
    try {
      NotNullDeserializeBinding binding = (NotNullDeserializeBinding)XmlSerializerImpl.serializer.getClassBinding(aClass);
      return (T)binding.deserialize(null, element);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException("Cannot deserialize class " + aClass.getName(), e);
    }
  }

  @NotNull
  public static <T> T deserialize(@NotNull URL url, Class<T> aClass) throws XmlSerializationException {
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
      getBeanBinding(bean).deserializeInto(bean, element);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  /**
   * Use only if it is a hot spot, otherwise use {@link #deserializeInto(Object, Element)} or {@link #serializeInto(Object, Element)}.
   */
  @ApiStatus.Experimental
  @NotNull
  public static BeanBinding getBeanBinding(@NotNull Object bean) {
    return (BeanBinding)XmlSerializerImpl.serializer.getClassBinding(bean.getClass());
  }

  public static void serializeInto(final Object bean, final Element element) {
    serializeInto(bean, element, null);
  }

  public static void serializeInto(@NotNull Object bean, @NotNull Element element, @Nullable SerializationFilter filter) {
    if (filter == null) {
      filter = TRUE_FILTER;
    }
    try {
      getBeanBinding(bean).serializeInto(bean, element, filter);
    }
    catch (XmlSerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }
}
