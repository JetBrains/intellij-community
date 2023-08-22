// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.rules;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.UsageViewSettings;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageGroupingRuleProvider {

  ExtensionPointName<UsageGroupingRuleProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageGroupingRuleProvider");

  /**
   * This is the entry point, other {@code getActiveRules} method is a simplified version.
   */
  @OverrideOnly
  default @NotNull UsageGroupingRule @NotNull [] getActiveRules(
    @NotNull Project project,
    @NotNull UsageViewSettings usageViewSettings,
    @Nullable UsageViewPresentation presentation
  ) {
    return getActiveRules(project, usageViewSettings);
  }

  @OverrideOnly
  default @NotNull UsageGroupingRule @NotNull [] getActiveRules(
    @NotNull Project project,
    @NotNull UsageViewSettings usageViewSettings
  ) {
    return getActiveRules(project);
  }

  @OverrideOnly
  default @NotNull UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project) {
    return UsageGroupingRule.EMPTY_ARRAY;
  }

  default @NotNull AnAction @NotNull [] createGroupingActions(@NotNull UsageView view) {
    return AnAction.EMPTY_ARRAY;
  }
}
