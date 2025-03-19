// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.project.Project;
import com.intellij.usages.*;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.impl.PackageGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class ActiveRules {
  public static UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project,
                                                             @NotNull UsageViewSettings usageViewSettings,
                                                             @Nullable UsageViewPresentation presentation,
                                                             boolean supportsNonCodeRule,
                                                             boolean supportsScopesRule,
                                                             boolean supportsModuleRule) {
    List<UsageGroupingRule> rules = new ArrayList<>();
    if (supportsNonCodeRule && (presentation == null || !presentation.isDetachedMode() && presentation.isNonCodeUsageAvailable())) {
      rules.add(new NonCodeUsageGroupingRule(presentation));
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
      UsageGroupingRule rule = DirectoryGroupingRule.getInstance(project);
      if (presentation != null) {
        for (PackageGroupRuleProvider ruleProvider : PackageGroupRuleProvider.EP_NAME.getExtensionList()) {
          UsageGroupingRule candidate = ruleProvider.getUsageGroupingRule(project, usageViewSettings, presentation.isDetachedMode());
          if (candidate == null) continue;
          rule = candidate;
          break;
        }
      }
      rules.add(rule);
    }
    if (usageViewSettings.isGroupByDirectoryStructure()) {
      rules.add(new DirectoryStructureGroupingRule(project, usageViewSettings.isCompactMiddleDirectories()));
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

  public static UsageGroupingRule @NotNull [] getAllRules(@NotNull Project project,
                                                          @NotNull UsageViewSettings usageViewSettings,
                                                          @Nullable UsageViewPresentation presentation,
                                                          boolean supportsNonCodeRule,
                                                          boolean supportsScopesRule,
                                                          boolean supportsModuleRule) {
    List<UsageGroupingRule> rules = new ArrayList<>();
    if (supportsNonCodeRule) {
      rules.add(new NonCodeUsageGroupingRule(presentation));
    }
    if (supportsScopesRule) {
      rules.add(new UsageScopeGroupingRule());
    }

    rules.add(new UsageTypeGroupingRule());

    if (supportsModuleRule) {
      rules.add(new ModuleGroupingRule(project, usageViewSettings.isFlattenModules()));
    }

    UsageGroupingRule packageRule = DirectoryGroupingRule.getInstance(project);
    if (presentation != null) {
      for (PackageGroupRuleProvider ruleProvider : PackageGroupRuleProvider.EP_NAME.getExtensionList()) {
        UsageGroupingRule rule = ruleProvider.getUsageGroupingRule(project, usageViewSettings, presentation.isDetachedMode());
        if (rule == null) continue;
        if (!(rule instanceof UsageGroupingRuleEx)) {
          rule = new PackageGroupingRuleExWrapper(rule);
        }
        packageRule = rule;
        break;
      }
    }
    rules.add(packageRule);

    rules.add(new DirectoryStructureGroupingRule(project, usageViewSettings.isCompactMiddleDirectories()));

    for (FileStructureGroupRuleProvider ruleProvider : FileStructureGroupRuleProvider.EP_NAME.getExtensionList()) {
      UsageGroupingRule rule = ruleProvider.getUsageGroupingRule(project, usageViewSettings);
      if (rule == null) continue;
      if (!(rule instanceof UsageGroupingRuleEx)) {
        rule = new FileStructureGroupingRuleExWrapper(rule);
      }
      rules.add(rule);
    }

    rules.add(new FileGroupingRule(project));

    return rules.toArray(UsageGroupingRule.EMPTY_ARRAY);
  }

  private abstract static class GroupingRuleExWrapper implements UsageGroupingRuleEx {
    private final UsageGroupingRule myGroupingRule;

    protected GroupingRuleExWrapper(@NotNull UsageGroupingRule rule) {
      myGroupingRule = rule;
    }

    @Override
    public @NotNull String getId() {
      return myGroupingRule.getClass().getName();
    }

    @Override
    public @NotNull @Unmodifiable List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
      return myGroupingRule.getParentGroupsFor(usage, targets);
    }

    @Override
    public int getRank() {
      return myGroupingRule.getRank();
    }
  }

  private static final class FileStructureGroupingRuleExWrapper extends GroupingRuleExWrapper {
    private FileStructureGroupingRuleExWrapper(@NotNull UsageGroupingRule rule) {
      super(rule);
    }

    @Override
    public @NotNull String getGroupingActionId() {
      return "UsageGrouping.FileStructure";
    }
  }

  private static final class PackageGroupingRuleExWrapper extends GroupingRuleExWrapper {
    private PackageGroupingRuleExWrapper(@NotNull UsageGroupingRule rule) {
      super(rule);
    }

    @Override
    public @NotNull String getGroupingActionId() {
      return "UsageGrouping.DirectoryStructure";
    }
  }
}
