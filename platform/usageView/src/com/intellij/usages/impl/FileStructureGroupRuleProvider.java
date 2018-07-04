/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.rules.UsageGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileStructureGroupRuleProvider {
  ExtensionPointName<FileStructureGroupRuleProvider> EP_NAME = new ExtensionPointName<>("com.intellij.fileStructureGroupRuleProvider");

  @Nullable
  UsageGroupingRule getUsageGroupingRule(@NotNull Project project);

  default UsageGroupingRule getUsageGroupingRule(@NotNull Project project, @NotNull UsageViewSettings usageViewSettings) {
    return getUsageGroupingRule(project);
  }
}
