// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkEx;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Provides extensions for OpenJDK API, implemented in JetBrains JDK.
 * For OpenJDK defaults to some meaningful results where applicable or otherwise throws runtime exception.
 */
public class JdkEx {
  @NotNull
  public static InputEventEx getInputEventEx() {
    if (SystemInfo.isJetBrainsJvm) {
      return new JBInputEventEx();
    }
    return new DefInputEventEx();
  }
}
