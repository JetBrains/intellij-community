/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public abstract class UsagesCollector {
  private static final Logger LOG = Logger.getInstance(UsagesCollector.class);

  private static final Object LOCK = new Object();

  public static final ExtensionPointName<UsagesCollector> EP_NAME = ExtensionPointName.create("com.intellij.statistics.usagesCollector");

  @NotNull
  public abstract Set<UsageDescriptor> getUsages() throws CollectUsagesException;

  @NotNull
  public abstract GroupDescriptor getGroupId();

  public static void doPersistProjectUsages(@NotNull Project project) {
    if (StatisticsUploadAssistant.isSendAllowed()) {
      synchronized (LOCK) {
        if (!project.isInitialized() || DumbService.isDumb(project)) {
          return;
        }

        for (UsagesCollector usagesCollector : EP_NAME.getExtensions()) {
          if (usagesCollector instanceof AbstractApplicationUsagesCollector) {
            ((AbstractApplicationUsagesCollector)usagesCollector).persistProjectUsages(project);
          }
        }
      }
    }
  }

  @NotNull
  public static Map<GroupDescriptor, Set<UsageDescriptor>> getAllUsages(@NotNull Set<String> disabledGroups) {
    synchronized (LOCK) {
      Map<GroupDescriptor, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (UsagesCollector usagesCollector : EP_NAME.getExtensions()) {
        GroupDescriptor groupDescriptor = usagesCollector.getGroupId();
        if (!disabledGroups.contains(groupDescriptor.getId())) {
          try {
            usageDescriptors.put(groupDescriptor, usagesCollector.getUsages());
          }
          catch (CollectUsagesException e) {
            LOG.info(e);
          }
        }
      }
      return usageDescriptors;
    }
  }
}
