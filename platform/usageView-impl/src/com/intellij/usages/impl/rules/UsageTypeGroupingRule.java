// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsageTypeGroupingRule extends SingleParentUsageGroupingRule implements UsageGroupingRuleEx {
  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (usage instanceof UsageWithType) {
      UsageType usageType = ((UsageWithType)usage).getUsageType();
      return usageType == null ? null : new UsageTypeGroup(usageType);
    }
    if (usage instanceof PsiElementUsage) {
      PsiElementUsage elementUsage = (PsiElementUsage)usage;

      PsiElement element = elementUsage.getElement();
      UsageType usageType = getUsageType(element, targets);

      if (usageType == null && element instanceof PsiFile && elementUsage instanceof UsageInfo2UsageAdapter) {
        usageType = ((UsageInfo2UsageAdapter)elementUsage).getUsageType();
      }

      if (usageType != null) return new UsageTypeGroup(usageType);

      if (usage instanceof ReadWriteAccessUsage) {
        ReadWriteAccessUsage u = (ReadWriteAccessUsage)usage;
        if (u.isAccessedForWriting()) return new UsageTypeGroup(UsageType.WRITE);
        if (u.isAccessedForReading()) return new UsageTypeGroup(UsageType.READ);
      }

      return new UsageTypeGroup(UsageType.UNCLASSIFIED);
    }

    return null;
  }

  @Nullable
  private static UsageType getUsageType(PsiElement element, UsageTarget @NotNull [] targets) {
    if (element == null) return null;

    if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) { return UsageType.COMMENT_USAGE; }

    for(UsageTypeProvider provider: UsageTypeProvider.EP_NAME.getExtensionList()) {
      UsageType usageType;
      if (provider instanceof UsageTypeProviderEx) {
        usageType = ((UsageTypeProviderEx) provider).getUsageType(element, targets);
      }
      else {
        usageType = provider.getUsageType(element);
      }
      if (usageType != null) {
        return usageType;
      }
    }

    return null;
  }

  @Override
  public int getRank() {
    return UsageGroupingRulesDefaultRanks.USAGE_TYPE.getAbsoluteRank();
  }

  @Override
  public @Nullable String getGroupingActionId() {
    return "UsageGrouping.UsageType";
  }

  private static final class UsageTypeGroup implements UsageGroup {
    private final UsageType myUsageType;

    private UsageTypeGroup(@NotNull UsageType usageType) {
      myUsageType = usageType;
    }

    @Override
    @NotNull
    public String getPresentableGroupText() {
      return myUsageType.toString();
    }

    @Override
    public void navigate(boolean focus) { }
    @Override
    public boolean canNavigate() { return false; }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public int compareTo(@NotNull UsageGroup usageGroup) {
      return getPresentableGroupText().compareTo(usageGroup.getPresentableGroupText());
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UsageTypeGroup)) return false;
      final UsageTypeGroup usageTypeGroup = (UsageTypeGroup)o;
      return myUsageType.equals(usageTypeGroup.myUsageType);
    }

    public int hashCode() {
      return myUsageType.hashCode();
    }

    @Override
    public String toString() {
      return UsageViewBundle.message("type.0", myUsageType.toString());
    }
  }
}
