// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.serialization;

public class SerializationException extends RuntimeException {
  public SerializationException() {
  }

  public SerializationException(String message) {
    super(message);
  }

  public SerializationException(String message, Throwable cause) {
    super(message, cause);
  }

  public SerializationException(Throwable cause) {
    super(cause);
  }
}
