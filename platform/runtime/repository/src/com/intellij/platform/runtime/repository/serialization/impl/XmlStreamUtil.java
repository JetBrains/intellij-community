// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public final class XmlStreamUtil {
  public static @NotNull String readFirstAttribute(@NotNull XMLStreamReader reader, @NotNull String attributeName) throws XMLStreamException {
    String name = reader.getAttributeLocalName(0);
    if (!attributeName.equals(name)) {
      throw new XMLStreamException("incorrect first attribute: " + attributeName + " expected but " + name + " found");
    }
    return reader.getAttributeValue(0);
  }
}
