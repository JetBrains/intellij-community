/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.persistence.ApplicationStatisticsPersistence;
import com.intellij.internal.statistic.persistence.CollectedUsages;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

// Collects(summarizes) project-level usages.
// Statistics is regularly sent  with some period of time (see com.intellij.internal.statistic.configurable.SendPeriod).
// In this period the user can open/close N projects which are not available at the "special send statistics" time.
// AbstractProjectsUsagesCollector persists the statistics data from opened/closed projects
// and summarizes collected results when data are requested.
//
// Example for groupId="my-team-lead-wants-something":
// 1. project A:  key_1= a1, key_2=b1, key_3=c1  // this project was opened and closed (not available just now)
// 2. project B:  key_1= a2, key_2=b2            // this project was opened and closed (not available just now)
// 3. project C:  key_1= a3, key_4=c3            // this project is open just now
// if "send statistics" action is invoked this UsageCollector returns :
// groupId="my-team-lead-wants-something":  key_1=(a1+a2+a3), key_2=(b1+b2), key_3=c1, key_4=c3
//
public abstract class AbstractProjectsUsagesCollector extends UsagesCollector {

  @NotNull
  public abstract Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException;

  private static final Logger LOG = Logger.getInstance(AbstractProjectsUsagesCollector.class);

  // Achtung!!! don't invoke this method directly even you can.
  void persistProjectUsages(@NotNull Project project) {
    try {
      persistProjectUsages(project, new CollectedUsages(getProjectUsages(project), System.currentTimeMillis()));
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private void persistProjectUsages(@NotNull Project project, @NotNull CollectedUsages usages) {
    persistProjectUsages(project, usages, ApplicationStatisticsPersistenceComponent.getInstance());
  }

  private void persistProjectUsages(@NotNull Project project,
                                   @NotNull CollectedUsages usages,
                                   @NotNull ApplicationStatisticsPersistence persistence) {
    persistence.persistUsages(getGroupId(), project, usages);
  }

  @NotNull
  private Set<UsageDescriptor> getApplicationUsages() {
    return mergeUsagesPostProcess(getApplicationUsages(ApplicationStatisticsPersistenceComponent.getInstance()));
  }

  @NotNull
  @Deprecated
  // this method should be overridden only in exceptional cases.
  // you have summarized usages from all opened/closed projects here.
  // do you want to add/merge something? Don't do it!!!
  protected Set<UsageDescriptor> mergeUsagesPostProcess(@NotNull Set<UsageDescriptor> usagesFromAllProjects) {
    return usagesFromAllProjects;
  }

  @NotNull
  private Set<UsageDescriptor> getApplicationUsages(@NotNull ApplicationStatisticsPersistence persistence) {
    ObjectIntHashMap<String> result = new ObjectIntHashMap<>();
    long lastTimeSent = UsageStatisticsPersistenceComponent.getInstance().getLastTimeSent();
    for (CollectedUsages usageDescriptors : persistence.getApplicationData(getGroupId()).values()) {
      if (!usageDescriptors.usages.isEmpty() && usageDescriptors.collectionTime > lastTimeSent) {
        result.ensureCapacity(usageDescriptors.usages.size());
        for (UsageDescriptor usageDescriptor : usageDescriptors.usages) {
          String key = usageDescriptor.getKey();
          result.put(key, result.get(key, 0) + usageDescriptor.getValue());
        }
      }
    }
    return StatisticsUtilKt.toUsageDescriptors(result);
  }

  @Override
  @NotNull
  public final Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    return getApplicationUsages();
  }
}
