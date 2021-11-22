// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public interface UsageFilteringRuleProvider {

  @Internal
  ExtensionPointName<UsageFilteringRuleProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageFilteringRuleProvider");

  @Internal
  Topic<Runnable> RULES_CHANGED = new Topic<>("usage view rules changed", Runnable.class);

  /**
   * @return read-only collection of available filtering rules for this {@code project}
   */
  default @NotNull Collection<? extends @NotNull UsageFilteringRule> getApplicableRules(@NotNull Project project) {
    return Collections.emptyList();
  }

  /**
   * @return array of active (enabled, turned on) filtering rules for this {@code project}
   * @deprecated implement/call {@link #getApplicableRules(Project)}
   */
  @Deprecated
  default UsageFilteringRule @NotNull [] getActiveRules(@SuppressWarnings("unused") @NotNull Project project) {
    return UsageFilteringRule.EMPTY_ARRAY;
  }

  /**
   * @return array of actions, which toggle some state causing {@link #getActiveRules(Project)} to return different set of rules
   * @deprecated implement {@link UsageFilteringRule#getActionId()} instead
   */
  @Deprecated
  default AnAction @NotNull [] createFilteringActions(@NotNull UsageView view) {
    return AnAction.EMPTY_ARRAY;
  }
}
