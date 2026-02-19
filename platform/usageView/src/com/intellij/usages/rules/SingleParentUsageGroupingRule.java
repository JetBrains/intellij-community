// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public abstract class SingleParentUsageGroupingRule implements UsageGroupingRule {
  /**
   * @return a group a specific usage should be placed into, or null, if this rule doesn't apply to this kind of usages.
   */
  protected abstract @Nullable UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets);

  @Override
  public final @NotNull @Unmodifiable List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    return ContainerUtil.createMaybeSingletonList(getParentGroupFor(usage, targets));
  }

  /**
   * @deprecated override {@link #getParentGroupFor(Usage, UsageTarget[])} instead
   */
  @Deprecated
  @Override
  public UsageGroup groupUsage(@NotNull Usage usage) {
    return getParentGroupFor(usage, UsageTarget.EMPTY_ARRAY);
  }
}
