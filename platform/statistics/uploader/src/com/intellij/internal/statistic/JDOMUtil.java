// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class JDOMUtil {
  private static final String XML_INPUT_FACTORY_KEY = "javax.xml.stream.XMLInputFactory";
  private static final String XML_INPUT_FACTORY_IMPL = "com.sun.xml.internal.stream.XMLInputFactoryImpl";

  private static volatile XMLInputFactory XML_INPUT_FACTORY;

  private static XMLInputFactory getXmlInputFactory() {
    XMLInputFactory factory = XML_INPUT_FACTORY;
    if (factory != null) {
      return factory;
    }

    //noinspection SynchronizeOnThis
    synchronized (JDOMUtil.class) {
      factory = XML_INPUT_FACTORY;
      if (factory != null) {
        return factory;
      }

      // requests default JRE factory implementation instead of an incompatible one from the classpath
      String property = System.setProperty(XML_INPUT_FACTORY_KEY, XML_INPUT_FACTORY_IMPL);
      try {
        factory = XMLInputFactory.newFactory();
      }
      finally {
        if (property != null) {
          System.setProperty(XML_INPUT_FACTORY_KEY, property);
        }
        else {
          System.clearProperty(XML_INPUT_FACTORY_KEY);
        }
      }

      //if (!SystemInfo.isIbmJvm) {
      //  try {
      //    factory.setProperty("http://java.sun.com/xml/stream/properties/report-cdata-event", true);
      //  }
      //  catch (Exception e) {
      //    getLogger().error("cannot set \"report-cdata-event\" property for XMLInputFactory", e);
      //  }
      //}

      factory.setProperty(XMLInputFactory.IS_COALESCING, true);
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
      XML_INPUT_FACTORY = factory;
      return factory;
    }
  }

  private JDOMUtil() { }

  @NotNull
  private static Element loadUsingStaX(@NotNull Reader reader, @Nullable SafeJdomFactory factory) throws JDOMException, IOException {
    try {
      XMLStreamReader xmlStreamReader = getXmlInputFactory().createXMLStreamReader(reader);
      try {
        return SafeStAXStreamBuilder.build(xmlStreamReader, true, factory == null ? SafeStAXStreamBuilder.FACTORY : factory);
      }
      finally {
        xmlStreamReader.close();
      }
    }
    catch (XMLStreamException e) {
      throw new JDOMException(e.getMessage(), e);
    }
    finally {
      reader.close();
    }
  }

  @Contract("null -> null; !null -> !null")
  public static Element load(InputStream stream) throws JDOMException, IOException {
    return stream == null ? null : load(stream, null);
  }

  /**
   * Internal use only.
   */
  @ApiStatus.Internal
  @NotNull
  public static Element load(@NotNull InputStream stream, @Nullable SafeJdomFactory factory) throws JDOMException, IOException {
    return loadUsingStaX(new InputStreamReader(stream, StandardCharsets.UTF_8), factory);
  }
}
