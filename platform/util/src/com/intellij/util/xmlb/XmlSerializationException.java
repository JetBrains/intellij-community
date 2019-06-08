// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xmlb;

import com.intellij.serialization.SerializationException;

public final class XmlSerializationException extends SerializationException {
  public XmlSerializationException() {
  }

  public XmlSerializationException(String message) {
    super(message);
  }

  public XmlSerializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public XmlSerializationException(Throwable cause) {
    super(cause);
  }
}
