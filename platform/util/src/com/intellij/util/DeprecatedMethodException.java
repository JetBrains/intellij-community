// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class DeprecatedMethodException extends RuntimeException {
  private static final Set<String> BEAT_DEAD_HORSE = ContainerUtil.newConcurrentSet();
  private static final Logger LOG = Logger.getInstance(DeprecatedMethodException.class);
  private DeprecatedMethodException(@NotNull String message) {
    super(message);
  }

  public static void report(@NotNull String message) {
    String text = "This method in " + ReflectionUtil.findCallerClass(2) +
                      " is deprecated and going to be removed soon. " + message;
    if (BEAT_DEAD_HORSE.add(text)) {
      LOG.warn(new DeprecatedMethodException(text));
    }
  }
}
