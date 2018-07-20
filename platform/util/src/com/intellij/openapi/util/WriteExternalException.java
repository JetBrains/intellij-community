// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

/**
 * Throw this exception from {@link JDOMExternalizable#writeExternal(org.jdom.Element)} method if you don't want to store any settings.
 * If you simply return from the method empty '<component name=... />' tag will be written leading to unneeded modification of configuration files.
 */
public class WriteExternalException extends RuntimeException {
  public WriteExternalException() {
    super();
  }

  public WriteExternalException(String s) {
    super(s);
  }

  public WriteExternalException(String message, Throwable cause) {
    super(message, cause);
  }
}
