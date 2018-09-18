// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class DeprecatedMethodException extends RuntimeException {
  private static final Logger LOG = Logger.getInstance(DeprecatedMethodException.class);
  private DeprecatedMethodException(String message) {
    super(message);
  }

  public static void report(@NotNull String message) {

    LOG.warn(new DeprecatedMethodException("This method in " + ReflectionUtil.findCallerClass(2) +
                                           " is going to be removed soon. "+message));
  }
}
