/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.internal.statistic.persistence.ApplicationStatisticsPersistenceComponent;
import com.intellij.internal.statistic.persistence.CollectedUsages;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ObjectIntHashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public abstract class AbstractApplicationUsagesCollector extends UsagesCollector {
  private static final Logger LOG = Logger.getInstance(AbstractApplicationUsagesCollector.class);

  public void persistProjectUsages(@NotNull Project project) {
    try {
      persistProjectUsages(project, new CollectedUsages(getProjectUsages(project), System.currentTimeMillis()));
    }
    catch (ProcessCanceledException e) {
      LOG.info(e);
    }
    catch (CollectUsagesException e) {
      LOG.info(e);
    }
    catch (Exception usageCollectorException) {
      LOG.info(usageCollectorException);
    }
  }

  public void persistProjectUsages(@NotNull Project project, @NotNull CollectedUsages usages) {
    persistProjectUsages(project, usages, ApplicationStatisticsPersistenceComponent.getInstance());
  }

  public void persistProjectUsages(@NotNull Project project,
                                   @NotNull CollectedUsages usages,
                                   @NotNull ApplicationStatisticsPersistence persistence) {
    persistence.persistUsages(getGroupId(), project, usages);
  }

  @NotNull
  public Set<UsageDescriptor> getApplicationUsages() {
    return getApplicationUsages(ApplicationStatisticsPersistenceComponent.getInstance());
  }

  @NotNull
  public Set<UsageDescriptor> getApplicationUsages(@NotNull ApplicationStatisticsPersistence persistence) {
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

    if (result.isEmpty()){
      return Collections.emptySet();
    }
    else {
      final THashSet<UsageDescriptor> descriptors = new THashSet<>(result.size());
      result.forEachEntry(new TObjectIntProcedure<String>() {
        @Override
        public boolean execute(String key, int value) {
          descriptors.add(new UsageDescriptor(key, value));
          return true;
        }
      });
      return descriptors;
    }
  }

  @Override
  @NotNull
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    return getApplicationUsages();
  }

  @NotNull
  public abstract Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException;
}
