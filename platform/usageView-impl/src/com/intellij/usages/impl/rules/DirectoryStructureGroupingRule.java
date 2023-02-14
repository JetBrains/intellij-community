// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.usages.rules.UsageInFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DirectoryStructureGroupingRule implements DumbAware, UsageGroupingRuleEx {
  protected final Project myProject;
  private final DirectoryGroupingRule myDirectoryGroupingRule;
  private final boolean compactMiddleDirectories;

  DirectoryStructureGroupingRule(@NotNull Project project, boolean compactMiddleDirectories) {
    myProject = project;
    this.compactMiddleDirectories = compactMiddleDirectories;
    myDirectoryGroupingRule = new DirectoryGroupingRule(project, false, compactMiddleDirectories);
  }

  @Override
  public @NotNull List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, UsageTarget @NotNull [] targets) {
    if (!(usage instanceof UsageInFile usageInFile)) {
      return Collections.emptyList();
    }
    List<UsageGroup> result = new ArrayList<>();
    VirtualFile file = usageInFile.getFile();
    if (file == null) {
      return Collections.emptyList();
    }
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    VirtualFile dir = file.getParent();

    if (compactMiddleDirectories) {
      UsageGroup group = myDirectoryGroupingRule.getGroupForFile(dir);
      result.add(group);
    }
    else {
      VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
      while (dir != null && !dir.equals(baseDir)) {
        UsageGroup group = myDirectoryGroupingRule.getGroupForFile(dir);
        result.add(group);
        dir = dir.getParent();
      }
    }
    Collections.reverse(result);
    return result;
  }

  @Override
  public int getRank() {
    return UsageGroupingRulesDefaultRanks.DIRECTORY_STRUCTURE.getAbsoluteRank();
  }

  @Override
  public @Nullable String getGroupingActionId() {
    return "UsageGrouping.DirectoryStructure";
  }
}
