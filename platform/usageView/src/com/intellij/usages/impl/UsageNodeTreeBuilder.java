// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class UsageNodeTreeBuilder {
  private final GroupNode myRoot;
  private final Project myProject;
  private final UsageTarget[] myTargets;
  private UsageGroupingRule[] myGroupingRules;
  private UsageFilteringRule[] myFilteringRules;

  UsageNodeTreeBuilder(UsageTarget @NotNull [] targets,
                       UsageGroupingRule @NotNull [] groupingRules,
                       UsageFilteringRule @NotNull [] filteringRules,
                       @NotNull GroupNode root,
                       @NotNull Project project) {
    myTargets = targets;
    myGroupingRules = groupingRules;
    myFilteringRules = filteringRules;
    myRoot = root;
    myProject = project;
  }

  public void setGroupingRules(UsageGroupingRule @NotNull [] rules) {
    myGroupingRules = rules;
  }

  void setFilteringRules(UsageFilteringRule @NotNull [] rules) {
    myFilteringRules = rules;
  }

  public boolean isVisible(@NotNull Usage usage) {
    return ContainerUtil.and(myFilteringRules, rule -> rule.isVisible(usage, myTargets));
  }

  UsageNode appendOrGet(@NotNull Usage usage,
                        boolean filterDuplicateLines,
                        @NotNull Consumer<? super Node> edtInsertedUnderQueue) {
    if (!isVisible(usage)) return null;

    final boolean dumb = DumbService.isDumb(myProject);

    GroupNode groupNode = myRoot;
    for (int i = 0; i < myGroupingRules.length; i++) {
      UsageGroupingRule rule = myGroupingRules[i];
      if (dumb && !DumbService.isDumbAware(rule)) continue;

      List<UsageGroup> groups = rule.getParentGroupsFor(usage, myTargets);
      for (UsageGroup group : groups) {
        groupNode = groupNode.addOrGetGroup(group, i, edtInsertedUnderQueue);
      }
    }

    return groupNode.addOrGetUsage(usage, filterDuplicateLines, edtInsertedUnderQueue);
  }
}
