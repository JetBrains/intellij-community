// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class DeprecatedMethodException extends RuntimeException {
  private static final Set<String> BEAT_DEAD_HORSE = ContainerUtil.newConcurrentSet();
  private static final Logger LOG = Logger.getInstance(DeprecatedMethodException.class);
  private DeprecatedMethodException(@NotNull String message) {
    super(message);
  }

  /**
   * This method reports the error only once for every same {@param message}
   */
  public static void report(@NotNull String message) {
    if (!BEAT_DEAD_HORSE.add(message)) return;
    Class<?> superClass = ReflectionUtil.findCallerClass(2);
    String superClassName = superClass != null ? superClass.getName() : "<no class>";
    String text = "This method in '" + superClassName +
                      "' is deprecated and going to be removed soon. " + message;
    LOG.warn(new DeprecatedMethodException(text));
  }

  /**
   * This method reports the error only for every same parameters
   */
  public static void reportDefaultImplementation(@NotNull Class<?> thisClass, @NotNull String methodName, @NotNull String message) {
    if (!BEAT_DEAD_HORSE.add(methodName + "###" + message + "###" + thisClass)) return;
    Class<?> superClass = ReflectionUtil.findCallerClass(2);
    String superClassName = superClass != null ? superClass.getName() : "<no class>";
    String text = "The default implementation of method '" + superClassName + "." + methodName + "' is deprecated, you need to override it in '" +
                  thisClass + "'. " + message;
    LOG.warn(new DeprecatedMethodException(text));
  }
}
