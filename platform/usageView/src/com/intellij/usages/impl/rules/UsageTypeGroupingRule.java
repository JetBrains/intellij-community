/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl.rules;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class UsageTypeGroupingRule implements UsageGroupingRule {
  public UsageGroup groupUsage(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      PsiElementUsage elementUsage = (PsiElementUsage)usage;

      UsageType usageType = getUsageType(elementUsage.getElement());
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
  private static UsageType getUsageType(PsiElement element) {
    if (element == null) return null;

    if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) { return UsageType.COMMENT_USAGE; }

    UsageTypeProvider[] providers = Extensions.getExtensions(UsageTypeProvider.EP_NAME);
    for(UsageTypeProvider provider: providers) {
      UsageType usageType = provider.getUsageType(element);
      if (usageType != null) {
        return usageType;
      }
    }

    return null;
  }




  private class UsageTypeGroup implements UsageGroup {
    private final UsageType myUsageType;

    private UsageTypeGroup(@NotNull UsageType usageType) {
      myUsageType = usageType;
    }

    public void update() {
    }

    public Icon getIcon(boolean isOpen) {
      return null;
    }

    @NotNull
    public String getText(UsageView view) {
      return myUsageType.toString();
    }

    public FileStatus getFileStatus() {
      return null;
    }

    public boolean isValid() { return true; }
    public void navigate(boolean focus) { }
    public boolean canNavigate() { return false; }

    public boolean canNavigateToSource() {
      return false;
    }

    public int compareTo(UsageGroup usageGroup) {
      return getText(null).compareTo(usageGroup.getText(null));
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UsageTypeGroup)) return false;
      final UsageTypeGroup usageTypeGroup = (UsageTypeGroup)o;
      if (myUsageType != null ? !myUsageType.equals(usageTypeGroup.myUsageType) : usageTypeGroup.myUsageType != null) return false;
      return true;
    }

    public int hashCode() {
      return myUsageType != null ? myUsageType.hashCode() : 0;
    }
  }
}
