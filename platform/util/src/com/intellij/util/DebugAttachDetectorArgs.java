// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;

/**
 * Finds the debugger arguments passed to the IDE at startup.
 */
@ApiStatus.Internal
public final class DebugAttachDetectorArgs {
  private static final @Nullable String DEBUG_ARGS = findDebugArgs();

  private static @Nullable String findDebugArgs() {
    try {
      for (String value : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
        if (value.contains("-agentlib:jdwp")) {
          return value;
        }
      }
    } catch (Exception e) {
      Logger.getInstance(DebugAttachDetectorArgs.class).error(e);
    }
    return null;
  }

  public static @Nullable String getDebugArgs() {
    return DEBUG_ARGS;
  }

  public static boolean isDebugEnabled() {
    return DEBUG_ARGS != null;
  }
}
