package com.intellij.usages.rules;

import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UsageGroupingRuleProviderEx extends UsageGroupingRuleProvider {
  /**
   * @return all rules that could be provided by this provider for given project and usage view, regardless of their enabled status
   */
  @NotNull UsageGroupingRule @NotNull [] getAllRules(@NotNull Project project, @Nullable UsageView usageView);
}
