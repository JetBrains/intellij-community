/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LogUtil {
  private LogUtil() { }

  public static String objectAndClass(@Nullable final Object o) {
    return o != null ? o + " (" + o.getClass().getName() + ")" : "null";
  }

  /**
   * Format string syntax as in {@linkplain String#format(String, Object...)}.
   */
  public static void debug(@NotNull Logger logger, @NotNull String format, @Nullable Object... args) {
    if (logger.isDebugEnabled()) {
      logger.debug(String.format(format, args));
    }
  }
}
