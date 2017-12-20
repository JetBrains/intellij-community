/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.rules;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public interface UsageGroupingRuleProvider {
  ExtensionPointName<UsageGroupingRuleProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageGroupingRuleProvider");

  @NotNull UsageGroupingRule[] getActiveRules(@NotNull Project project);

  default @NotNull UsageGroupingRule[] getActiveRules(@NotNull Project project, @NotNull UsageViewSettings usageViewSettings) {
    return getActiveRules(project);
  }

  @NotNull
  AnAction[] createGroupingActions(UsageView view);
}
