/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.SingleParentUsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class NonCodeUsageGroupingRule extends SingleParentUsageGroupingRule {
  private final Project myProject;

  public NonCodeUsageGroupingRule(Project project) {
    myProject = project;
  }

  private static class CodeUsageGroup extends UsageGroupBase {
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
      //noinspection HardCodedStringLiteral
      return "CodeUsages";
    }
  }

  private static class UsageInGeneratedCodeGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new UsageInGeneratedCodeGroup();

    private UsageInGeneratedCodeGroup() {
      super(4);
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return view == null ? UsageViewBundle.message("node.usages.in.generated.code") : view.getPresentation().getUsagesInGeneratedCodeString();
    }

    public String toString() {
      return "UsagesInGeneratedCode";
    }
  }

  private static class NonCodeUsageGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new NonCodeUsageGroup();

    private NonCodeUsageGroup() {
      super(3);
    }

    @Override
    @NotNull
    public String getText(UsageView view) {
      return view == null ? UsageViewBundle.message("node.non.code.usages") : view.getPresentation().getNonCodeUsagesString();
    }

    @Override
    public void update() {
    }

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "NonCodeUsages";
    }
  }

  private static class DynamicUsageGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new DynamicUsageGroup();
    @NonNls private static final String DYNAMIC_CAPTION = "Dynamic usages";

    public DynamicUsageGroup() {
      super(2);
    }

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

    public String toString() {
      //noinspection HardCodedStringLiteral
      return "DynamicUsages";
    }
  }

  private static class UnloadedModulesUsageGroup extends UsageGroupBase {
    public static final UsageGroup INSTANCE = new UnloadedModulesUsageGroup();

    public UnloadedModulesUsageGroup() {
      super(0);
    }

    @NotNull
    @Override
    public String getText(@Nullable UsageView view) {
      return "Usages in Unloaded Modules";
    }

    @Override
    public String toString() {
      return getText(null);
    }
  }

  @Nullable
  @Override
  protected UsageGroup getParentGroupFor(@NotNull Usage usage, @NotNull UsageTarget[] targets) {
    if (usage instanceof UnknownUsagesInUnloadedModules) {
      return UnloadedModulesUsageGroup.INSTANCE;
    }
    if (usage instanceof UsageInFile) {
      VirtualFile file = ((UsageInFile)usage).getFile();
      if (file != null && GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
          return UsageInGeneratedCodeGroup.INSTANCE;
      }
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
}
