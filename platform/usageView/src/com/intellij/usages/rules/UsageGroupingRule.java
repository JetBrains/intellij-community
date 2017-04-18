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
package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A rule specifying how specific Usage View elements should be grouped. 
 * 
 * During indexing, only instances that implement {@link com.intellij.openapi.project.DumbAware} are executed. 
 */
public interface UsageGroupingRule {
  UsageGroupingRule[] EMPTY_ARRAY = new UsageGroupingRule[0];

  /**
   * @return a group a specific usage should be placed into, or null, if this rule doesn't apply to this kind of usages.
   */
  @Nullable
  UsageGroup groupUsage(@NotNull Usage usage);

  /**
   * @return list of nested parent groups for a usage. The specified usage will be placed into the last group from the list, that group
   * will be placed under the next to last group, etc.
   */
  @NotNull
  default List<UsageGroup> getParentGroupsFor(@NotNull Usage usage, @NotNull UsageTarget[] targets) {
    return ContainerUtil.createMaybeSingletonList(groupUsage(usage));
  }
}
