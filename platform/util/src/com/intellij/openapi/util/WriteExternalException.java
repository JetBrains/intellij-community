// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;

/**
 * Throw this exception from {@link JDOMExternalizable#writeExternal(org.jdom.Element)} method if you don't want to store any settings.
 * If you simply return from the method empty {@code '<component name=... />'} tag will be written leading to unneeded modification of configuration files.
 */
public class WriteExternalException extends RuntimeException {
  /**
   * @deprecated Do not use WriteExternalException as a control flow exception.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
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
