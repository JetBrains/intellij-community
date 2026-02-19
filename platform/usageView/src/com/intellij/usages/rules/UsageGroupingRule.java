// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.rules;

import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A rule specifying how specific Usage View elements should be grouped. 
 * 
 * During indexing, only instances that implement {@link com.intellij.openapi.project.DumbAware} are executed. 
 */
public interface UsageGroupingRule extends PossiblyDumbAware {
  UsageGroupingRule[] EMPTY_ARRAY = new UsageGroupingRule[0];

  /**
   * Return list of nested parent groups for a usage. The specified usage will be placed into the last group from the list, that group
   * will be placed under the next to last group, etc.
   * <p>If the rule returns at most one parent group extend {@link SingleParentUsageGroupingRule} and override
   * {@link SingleParentUsageGroupingRule#getParentGroupFor getParentGroupFor} instead.</p>
   */
  default @NotNull @Unmodifiable List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    return ContainerUtil.createMaybeSingletonList(groupUsage(usage));
  }

  /**
   * Override this method to change order in which rules are applied. Rules with smaller ranks are applied earlier, i.e. parent groups
   * returned by them will be placed closer to the tree root.
   */
  default int getRank() {
    return Integer.MAX_VALUE;
  }

  /**
   * @deprecated extend {@link SingleParentUsageGroupingRule} and override {@link SingleParentUsageGroupingRule#getParentGroupFor getParentGroupFor} instead
   */
  @Deprecated
  default @Nullable UsageGroup groupUsage(@NotNull Usage usage) {
    throw new UnsupportedOperationException();
  }
}
