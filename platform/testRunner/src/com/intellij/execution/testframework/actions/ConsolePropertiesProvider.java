// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.testframework.TestConsoleProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConsolePropertiesProvider {
  /**
   * @return nonnull value if IDEA should build tree view
   *         null otherwise
   */
  default @Nullable TestConsoleProperties createTestConsoleProperties(@NotNull Executor executor) {
    return null;
  }
}
