// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;

public interface UsageFilteringRule {
  UsageFilteringRule[] EMPTY_ARRAY = new UsageFilteringRule[0];

  default boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    return isVisible(usage);
  }

  /**
   * @deprecated implement {@link #isVisible(Usage, UsageTarget[])} instead
   */
  @Deprecated
  default boolean isVisible(@NotNull Usage usage) {
    throw new UnsupportedOperationException();
  }
}
