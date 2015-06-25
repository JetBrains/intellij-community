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
package com.intellij.usages.impl;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleEx;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleEx;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
class UsageNodeTreeBuilder {
  private final GroupNode myRoot;
  private final Project myProject;
  private final UsageTarget[] myTargets;
  private UsageGroupingRule[] myGroupingRules;
  private UsageFilteringRule[] myFilteringRules;

  UsageNodeTreeBuilder(@NotNull UsageTarget[] targets,
                       @NotNull UsageGroupingRule[] groupingRules,
                       @NotNull UsageFilteringRule[] filteringRules,
                       @NotNull GroupNode root, 
                       @NotNull Project project) {
    myTargets = targets;
    myGroupingRules = groupingRules;
    myFilteringRules = filteringRules;
    myRoot = root;
    myProject = project;
  }

  public void setGroupingRules(@NotNull UsageGroupingRule[] rules) {
    myGroupingRules = rules;
  }

  public void setFilteringRules(@NotNull UsageFilteringRule[] rules) {
    myFilteringRules = rules;
  }

  public boolean isVisible(@NotNull Usage usage) {
    for (final UsageFilteringRule rule : myFilteringRules) {
      final boolean visible;
      if (rule instanceof UsageFilteringRuleEx) {
        visible = ((UsageFilteringRuleEx) rule).isVisible(usage, myTargets);
      }
      else {
        visible = rule.isVisible(usage);
      }
      if (!visible) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  UsageNode appendUsage(@NotNull Usage usage, @NotNull Consumer<Runnable> edtQueue) {
    if (!isVisible(usage)) return null;

    final boolean dumb = DumbService.isDumb(myProject);

    GroupNode lastGroupNode = myRoot;
    for (int i = 0; i < myGroupingRules.length; i++) {
      final UsageGroupingRule rule = myGroupingRules[i];
      if (dumb && !DumbService.isDumbAware(rule)) continue;
      
      final UsageGroup group;
      if (rule instanceof UsageGroupingRuleEx) {
        group = ((UsageGroupingRuleEx) rule).groupUsage(usage, myTargets);
      }
      else {
        group = rule.groupUsage(usage);
      }
      if (group != null) {
        lastGroupNode = lastGroupNode.addGroup(group, i, edtQueue);
      }
    }

    return lastGroupNode.addUsage(usage, edtQueue);
  }
}
