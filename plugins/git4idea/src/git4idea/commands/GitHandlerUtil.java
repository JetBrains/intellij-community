// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link GitImpl}
 */
@Deprecated
public final class GitHandlerUtil {

  private GitHandlerUtil() {
  }

  public static boolean isErrorLine(@NotNull String text) {
    for (String prefix : GitImplBase.ERROR_INDICATORS) {
      if (text.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
