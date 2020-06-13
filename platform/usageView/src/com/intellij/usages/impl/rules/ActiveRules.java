// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class ActiveRules {
  public static UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project,
                                                             @NotNull UsageViewSettings usageViewSettings,
                                                             boolean supportsNonCodeRule,
                                                             boolean supportsScopesRule,
                                                             boolean supportsModuleRule) {
    List<UsageGroupingRule> rules = new ArrayList<>();
    if (supportsNonCodeRule) {
      rules.add(new NonCodeUsageGroupingRule(project));
    }
    if (supportsScopesRule && usageViewSettings.isGroupByScope()) {
      rules.add(new UsageScopeGroupingRule());
    }
    if (usageViewSettings.isGroupByUsageType()) {
      rules.add(new UsageTypeGroupingRule());
    }
    if (supportsModuleRule && usageViewSettings.isGroupByModule()) {
      rules.add(new ModuleGroupingRule(project, usageViewSettings.isFlattenModules()));
    }
    if (usageViewSettings.isGroupByPackage() && !usageViewSettings.isGroupByDirectoryStructure()) {
      rules.add(DirectoryGroupingRule.getInstance(project));
    }
    if (usageViewSettings.isGroupByDirectoryStructure()) {
      rules.add(new DirectoryStructureGroupingRule(project));
    }
    if (usageViewSettings.isGroupByFileStructure()) {
      for (FileStructureGroupRuleProvider ruleProvider : FileStructureGroupRuleProvider.EP_NAME.getExtensionList()) {
        ContainerUtil.addIfNotNull(rules, ruleProvider.getUsageGroupingRule(project, usageViewSettings));
      }
    }
    else {
      rules.add(new FileGroupingRule(project));
    }

    return rules.toArray(UsageGroupingRule.EMPTY_ARRAY);
  }
}