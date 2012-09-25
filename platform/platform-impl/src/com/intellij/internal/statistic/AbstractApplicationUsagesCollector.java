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

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.persistence.ApplicationStatisticsPersistence;
import com.intellij.internal.statistic.persistence.ApplicationStatisticsPersistenceComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public abstract class AbstractApplicationUsagesCollector extends UsagesCollector {
    private static final Logger LOG = Logger.getInstance("#com.intellij.internal.statistic.AbstractApplicationUsagesCollector");

    public void persistProjectUsages(@NotNull Project project) {
      try {
        final Set<UsageDescriptor> projectUsages = getProjectUsages(project);
        persistProjectUsages(project, projectUsages);
      }
      catch (CollectUsagesException e) {
        LOG.info(e);
      }
    }

    public void persistProjectUsages(@NotNull Project project, @NotNull Set<UsageDescriptor> usages) {
        persistProjectUsages(project, usages, ApplicationStatisticsPersistenceComponent.getInstance());
    }

    public void persistProjectUsages(@NotNull Project project,
                                     @NotNull Set<UsageDescriptor> usages,
                                     @NotNull ApplicationStatisticsPersistence persistence) {
        persistence.persistUsages(getGroupId(), project, usages);
    }

    @NotNull
    public Set<UsageDescriptor> getApplicationUsages() {
        return getApplicationUsages(ApplicationStatisticsPersistenceComponent.getInstance());
    }

    @NotNull
    public Set<UsageDescriptor> getApplicationUsages(@NotNull final ApplicationStatisticsPersistence persistence) {
        final Map<String, Integer> result = new HashMap<String, Integer>();

        for (Set<UsageDescriptor> usageDescriptors : persistence.getApplicationData(getGroupId()).values()) {
            for (UsageDescriptor usageDescriptor : usageDescriptors) {
                final String key = usageDescriptor.getKey();
                final Integer count = result.get(key);
                result.put(key, count == null ? 1 : count.intValue() + 1);
            }
        }

        return ContainerUtil.map2Set(result.entrySet(), new Function<Map.Entry<String, Integer>, UsageDescriptor>() {
            @Override
            public UsageDescriptor fun(Map.Entry<String, Integer> entry) {
                return new UsageDescriptor(entry.getKey(), entry.getValue());
            }
        });
    }

    @Override
    @NotNull
    public Set<UsageDescriptor> getUsages(@Nullable Project project) throws CollectUsagesException {
        if (project != null) {
          final Set<UsageDescriptor> projectUsages = getProjectUsages(project);
          persistProjectUsages(project, projectUsages);
        }

        return getApplicationUsages();
    }

    @NotNull
    public abstract Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException;
}
