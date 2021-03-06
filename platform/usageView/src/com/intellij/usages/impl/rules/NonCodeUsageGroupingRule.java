// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class NonCodeUsageGroupingRule extends SingleParentUsageGroupingRule implements UsageGroupingRuleEx {

  private static final class CodeUsageGroup extends UsageGroupBase {
    private static final UsageGroup INSTANCE = new CodeUsageGroup();

    private CodeUsageGroup() {
      super(1);
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return view == null ? UsageViewBundle.message("node.group.code.usages") : view.getPresentation().getCodeUsagesString();
    }

    public String toString() {
      return "CodeUsages";
    }
  }

  private static final class NonCodeUsageGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new NonCodeUsageGroup();

    private NonCodeUsageGroup() {
      super(3);
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return view == null ? UsageViewBundle.message("node.non.code.usages") : view.getPresentation().getNonCodeUsagesString();
    }

    public String toString() {
      return "NonCodeUsages";
    }
  }

  private static class DynamicUsageGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new DynamicUsageGroup();

    DynamicUsageGroup() {
      super(2);
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      if (view != null) {
        String dynamicCodeUsagesString = view.getPresentation().getDynamicCodeUsagesString();
        if (dynamicCodeUsagesString != null) {
          return dynamicCodeUsagesString;
        }
      }
      return UsageViewBundle.message("list.item.dynamic.usages");
    }

    public String toString() {
      return "DynamicUsages";
    }
  }

  private static class UnloadedModulesUsageGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new UnloadedModulesUsageGroup();

    UnloadedModulesUsageGroup() {
      super(0);
    }

    @NotNull
    @Override
    public String getText(@Nullable UsageView view) {
      return UsageViewBundle.message("list.item.usages.in.unloaded.modules");
    }

    @Override
    public String toString() {
      return getText(null);
    }
  }

  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (usage instanceof UnknownUsagesInUnloadedModules) {
      return UnloadedModulesUsageGroup.INSTANCE;
    }
    if (usage instanceof PsiElementUsage) {
      if (usage instanceof UsageInfo2UsageAdapter) {
        final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
        if (usageInfo.isDynamicUsage()) {
          return DynamicUsageGroup.INSTANCE;
        }
      }
      if (((PsiElementUsage)usage).isNonCodeUsage()) {
        return NonCodeUsageGroup.INSTANCE;
      }
      else {
        return CodeUsageGroup.INSTANCE;
      }
    }
    return null;
  }

  @Override
  public boolean isGroupingToggleable() {
    return false;
  }
}
