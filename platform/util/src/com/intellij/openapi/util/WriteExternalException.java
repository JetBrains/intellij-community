// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

/**
 * Throw this exception from {@link JDOMExternalizable#writeExternal(org.jdom.Element)} method if you don't want to store any settings.
 * If you simply return from the method empty {@code '<component name=... />'} tag will be written leading to unneeded modification of configuration files.
 */
public final class WriteExternalException extends RuntimeException {
  public WriteExternalException(String s) {
    super(s);
  }

  public WriteExternalException(String message, Throwable cause) {
    super(message, cause);
  }
}
