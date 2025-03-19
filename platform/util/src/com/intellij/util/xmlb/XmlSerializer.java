// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.serialization.SerializationException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class XmlSerializer {
  private XmlSerializer() {
  }

  /**
   * Consider to use {@link SkipDefaultValuesSerializationFilters}
   */
  public static Element serialize(@NotNull Object object) throws SerializationException {
    return serialize(object, null);
  }

  public static @NotNull Element serialize(@NotNull Object object, @Nullable SerializationFilter filter) throws SerializationException {
    return XmlSerializerImpl.serialize(object, filter);
  }

  public static @NotNull <T> T deserialize(Document document, Class<T> aClass) throws SerializationException {
    return deserialize(document.getRootElement(), aClass);
  }

  @SuppressWarnings("unchecked")
  public static @NotNull <T> T deserialize(@NotNull Element element, @NotNull Class<T> aClass) throws SerializationException {
    try {
      Binding binding = XmlSerializerImpl.serializer.getRootBinding(aClass, aClass);
      return (T)Objects.requireNonNull(binding.deserialize(null, element, JdomAdapter.INSTANCE));
    }
    catch (SerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException("Cannot deserialize class " + aClass.getName(), e);
    }
  }

  public static @NotNull <T> T deserialize(@NotNull URL url, Class<T> aClass) throws SerializationException {
    try {
      return deserialize(JDOMUtil.load(url), aClass);
    }
    catch (IOException | JDOMException e) {
      throw new XmlSerializationException(e);
    }
  }

  public static void deserializeInto(@NotNull Object bean, @NotNull Element element) {
    try {
      Class<?> aClass = bean.getClass();
      ((BeanBinding)XmlSerializerImpl.serializer.getRootBinding(aClass, aClass)).deserializeInto(bean, element);
    }
    catch (SerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }

  @ApiStatus.Internal
  @ApiStatus.Obsolete
  public static @NotNull BeanBinding getBeanBinding(@NotNull Class<?> aClass) {
    return (BeanBinding)XmlSerializerImpl.serializer.getRootBinding(aClass, aClass);
  }

  public static void serializeInto(@NotNull Object bean, @NotNull Element element) {
    serializeInto(bean, element, null);
  }

  public static void serializeInto(@NotNull Object bean, @NotNull Element element, @Nullable SerializationFilter filter) {
    try {
      Class<?> aClass = bean.getClass();
      ((BeanBinding)XmlSerializerImpl.serializer.getRootBinding(aClass, aClass)).serializeProperties(bean, element, filter);
    }
    catch (SerializationException e) {
      throw e;
    }
    catch (Exception e) {
      throw new XmlSerializationException(e);
    }
  }
}
