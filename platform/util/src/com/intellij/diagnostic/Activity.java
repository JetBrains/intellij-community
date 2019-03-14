// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Activity {
  void end(@Nullable String description);

  /**
   * Convenient method to end token and start a new sibling one.
   * So, start of new is always equals to this item end and yet another System.nanoTime() call is avoided.
   */
  @NotNull
  Activity endAndStart(@NotNull String name);

  void endWithThreshold(@NotNull Class<?> clazz);

  @NotNull
  Activity startChild(@NotNull String name);

  default void end() {
    end(null);
  }
}
