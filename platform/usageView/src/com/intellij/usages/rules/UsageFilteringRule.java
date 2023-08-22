// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface UsageFilteringRule {

  UsageFilteringRule[] EMPTY_ARRAY = new UsageFilteringRule[0];

  /**
   * @return unique identifier of this rule which is used to persist the state (active/inactive)
   */
  default @NonNls @NotNull String getRuleId() {
    return getClass().getName();
  }

  /**
   * @return id of an action which will be used to obtain the presentation and the shortcut,
   * or empty string if the rule is not updated, meaning that
   * its action is still created via deprecated {@link UsageFilteringRuleProvider#createFilteringActions(UsageView)}
   */
  default @NotNull String getActionId() {
    return "";
  }

  default boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    return isVisible(usage);
  }

  default boolean isVisible(@NotNull Usage usage) {
    throw new AbstractMethodError("isVisible(Usage) or isVisible(Usage, UsageTarget[]) must be implemented");
  }
}
