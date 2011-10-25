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

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class NonCodeUsageGroupingRule implements UsageGroupingRule {
  private static class CodeUsageGroup implements UsageGroup {
    private static final UsageGroup INSTANCE = new CodeUsageGroup();

    @Override
    @NotNull
    public String getText(UsageView view) {
      return view == null ? UsageViewBundle.message("node.group.code.usages") : view.getPresentation().getCodeUsagesString();
    }

    @Override
    public void update() {
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "CodeUsages";
    }

    @Override
    public Icon getIcon(boolean isOpen) { return null; }
    @Override
    public FileStatus getFileStatus() { return null; }
    @Override
    public boolean isValid() { return true; }
    @Override
    public int compareTo(UsageGroup usageGroup) {
      if (usageGroup instanceof DynamicUsageGroup) {
        return -1;
      }
      return usageGroup == this ? 0 : 1;
    }
    @Override
    public void navigate(boolean requestFocus) { }
    @Override
    public boolean canNavigate() { return false; }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }
  }

  private static class NonCodeUsageGroup implements UsageGroup {
    public static final UsageGroup INSTANCE = new NonCodeUsageGroup();

    @Override
    @NotNull
    public String getText(UsageView view) {
      return view == null ? UsageViewBundle.message("node.group.code.usages") : view.getPresentation().getNonCodeUsagesString();
    }

    @Override
    public void update() {
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "NonCodeUsages";
    }
    @Override
    public Icon getIcon(boolean isOpen) { return null; }
    @Override
    public FileStatus getFileStatus() { return null; }
    @Override
    public boolean isValid() { return true; }
    @Override
    public int compareTo(UsageGroup usageGroup) { return usageGroup == this ? 0 : -1; }
    @Override
    public void navigate(boolean requestFocus) { }
    @Override
    public boolean canNavigate() { return false; }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }
  }

  private static class DynamicUsageGroup implements UsageGroup {
    public static final UsageGroup INSTANCE = new DynamicUsageGroup();
    @NonNls private static final String DYNAMIC_CAPTION = "Dynamic usages";

    @Override
    @NotNull
    public String getText(UsageView view) {
      if (view == null) {
        return DYNAMIC_CAPTION;
      }
      else {
        final String dynamicCodeUsagesString = view.getPresentation().getDynamicCodeUsagesString();
        return dynamicCodeUsagesString == null ? DYNAMIC_CAPTION : dynamicCodeUsagesString;
      }
    }

    @Override
    public void update() {
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "DynamicUsages";
    }
    @Override
    public Icon getIcon(boolean isOpen) { return null; }
    @Override
    public FileStatus getFileStatus() { return null; }
    @Override
    public boolean isValid() { return true; }
    @Override
    public int compareTo(UsageGroup usageGroup) { return usageGroup == this ? 0 : 1; }
    @Override
    public void navigate(boolean requestFocus) { }
    @Override
    public boolean canNavigate() { return false; }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }
  }

  @Override
  public UsageGroup groupUsage(@NotNull Usage usage) {
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
}
