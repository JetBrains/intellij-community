// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

public interface UsageViewElementsListener {
  ExtensionPointName<UsageViewElementsListener> EP_NAME = ExtensionPointName.create("com.intellij.usageViewElementsListener");

  /**
   * Excludes usage from usage view. Consider using {@link UsageFilteringRuleProvider} instead.
   */
  default boolean skipUsage(@NotNull UsageView view, @NotNull Usage usage) {
    return false;
  }

  default void beforeUsageAdded(@NotNull UsageView view, @NotNull Usage usage) {}

  default boolean isExcludedByDefault(@NotNull UsageView view, @NotNull Usage usage) {
    return false;
  }
}
