// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.util.NlsContexts.ListItem;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

class NonCodeUsageGroupingRule extends SingleParentUsageGroupingRule implements UsageGroupingRuleEx {

  private static final class CodeUsageGroup extends DefaultUsageGroup {

    CodeUsageGroup(@NotNull Supplier<@ListItem @NotNull String> textSupplier) {
      super(1, textSupplier);
    }

    @Override
    public String toString() {
      return "CodeUsages";
    }
  }

  private static final class DynamicUsageGroup extends DefaultUsageGroup {

    DynamicUsageGroup(@NotNull Supplier<@ListItem @NotNull String> supplier) {
      super(2, supplier);
    }

    public String toString() {
      return "DynamicUsages";
    }
  }

  private static final class NonCodeUsageGroup extends DefaultUsageGroup {

    NonCodeUsageGroup(@NotNull Supplier<@ListItem @NotNull String> supplier) {
      super(3, supplier);
    }

    public String toString() {
      return "NonCodeUsages";
    }
  }

  private static final UsageGroup ourUnloadedGroup = new DefaultUsageGroup(
    0, UsageViewBundle.messagePointer("list.item.usages.in.unloaded.modules")
  ) {
    @Override
    public String toString() {
      return getPresentableGroupText();
    }
  };

  private static final UsageGroup ourCodeGroup = new CodeUsageGroup(UsageViewBundle.messagePointer("node.group.code.usages"));
  private static final UsageGroup ourDynamicGroup = new DynamicUsageGroup(UsageViewBundle.messagePointer("list.item.dynamic.usages"));
  private static final UsageGroup ourNonCodeGroup = new NonCodeUsageGroup(UsageViewBundle.messagePointer("node.non.code.usages"));

  private final UsageGroup myCodeGroup;
  private final UsageGroup myDynamicCodeGroup;
  private final UsageGroup myNonCodeGroup;

  NonCodeUsageGroupingRule(@Nullable UsageViewPresentation presentation) {
    if (presentation == null) {
      myCodeGroup = ourCodeGroup;
      myDynamicCodeGroup = ourDynamicGroup;
      myNonCodeGroup = ourNonCodeGroup;
    }
    else {
      myCodeGroup = new CodeUsageGroup(() -> buildText(presentation.getCodeUsagesString(), presentation.getScopeText()));
      myDynamicCodeGroup = new DynamicUsageGroup(() -> {
        String dynamicCodeUsagesString = presentation.getDynamicCodeUsagesString();
        return dynamicCodeUsagesString != null ? dynamicCodeUsagesString : ourDynamicGroup.getPresentableGroupText();
      });
      myNonCodeGroup = new NonCodeUsageGroup(() -> buildText(presentation.getNonCodeUsagesString(), presentation.getScopeText()));
    }
  }

  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (usage instanceof UnknownUsagesInUnloadedModules) {
      return ourUnloadedGroup;
    }
    if (usage instanceof PsiElementUsage) {
      if (usage instanceof UsageInfo2UsageAdapter) {
        final UsageInfo usageInfo = ((UsageInfo2UsageAdapter)usage).getUsageInfo();
        if (usageInfo.isDynamicUsage()) {
          return myDynamicCodeGroup;
        }
      }
      if (((PsiElementUsage)usage).isNonCodeUsage()) {
        return myNonCodeGroup;
      }
      else {
        return myCodeGroup;
      }
    }
    return null;
  }

  @Override
  public int getRank() {
    return UsageGroupingRulesDefaultRanks.NON_CODE.getAbsoluteRank();
  }

  @Override
  public boolean isGroupingToggleable() {
    return false;
  }

  @Nls
  private static String buildText(String usages, String scope) {
    @NlsSafe StringBuilder text = new StringBuilder(usages);
    text.append(" ").append(UsageViewBundle.message("usage.view.results.node.scope.in"));

    if (StringUtil.isNotEmpty(scope)) {
      text.append(" ").append(scope);
    }
    return text.toString();
  }
}
