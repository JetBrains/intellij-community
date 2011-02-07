/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.statistics;

import com.intellij.openapi.project.Project;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public abstract class VcsStatisticsPersistence {
  private Map<String, Set<UsageDescriptor>> myVcsUsagesMap = new HashMap<String, Set<UsageDescriptor>>();

  public VcsStatisticsPersistence() {
  }

  public void persist(@NotNull Project project, @NotNull Set<UsageDescriptor> vcs) {
    myVcsUsagesMap.put(project.getName(), vcs);
  }

  @NotNull
  public Map<String, Set<UsageDescriptor>> getVcsUsageMap() {
    return myVcsUsagesMap;
  }

}
