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

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.PatchedUsage;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsHttpClientSender;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.SentUsagesPersistence;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.Time;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StatisticsUploadAssistant {

    private static final Logger LOG = Logger.getInstance("#com.intellij.internal.statistic.StatisticsUploadAssistant");

    public String getData() {
        return getData(Collections.<String>emptySet());
    }

    public static boolean showNotification() {
        return UsageStatisticsPersistenceComponent.getInstance().isShowNotification() &&
               (System.currentTimeMillis() - Time.DAY > ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFirstRunTime()) ;
    }

    public static boolean isTimeToSend() {
        return isTimeToSend(UsageStatisticsPersistenceComponent.getInstance());
    }

    public static boolean isTimeToSend(UsageStatisticsPersistenceComponent settings) {
        final long timeDelta = System.currentTimeMillis() - settings.getLastTimeSent();

        return Math.abs(timeDelta) > settings.getPeriod().getMillis();
    }

    public static boolean isSendAllowed() {
        return isSendAllowed(UsageStatisticsPersistenceComponent.getInstance());
    }

    public static boolean isSendAllowed(final SentUsagesPersistence settings) {
        return settings != null && settings.isAllowed();
    }

    public String getData(@NotNull Set<String> disabledGroups) {
        return getStringPatch(disabledGroups, ProjectManager.getInstance().getOpenProjects());
    }

    public static void persistSentPatch(@NotNull String patchStr) {
        persistSentPatch(patchStr, UsageStatisticsPersistenceComponent.getInstance());
    }

    public static void persistSentPatch(@NotNull String patchStr, @NotNull SentUsagesPersistence persistenceComponent) {
        Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = mapToPatchedUsagesMap(ConvertUsagesUtil.convertString(patchStr));

        if (patchedUsages.size() > 0) persistenceComponent.persistPatch(patchedUsages);
    }

    @NotNull
    public static String getStringPatch(@NotNull Set<String> disabledGroups, Project... project) {
        return getStringPatch(disabledGroups, project, UsageStatisticsPersistenceComponent.getInstance(), 0);
    }

    @NotNull
    public static String getStringPatch(@NotNull Set<String> disabledGroups,
                                        @NotNull Project[] projects,
                                        @NotNull SentUsagesPersistence usagesPersistence,
                                        int maxSize) {
        final Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = getPatchedUsages(disabledGroups, projects, usagesPersistence);

        return getStringPatch(patchedUsages, maxSize);
    }

    public static String getStringPatch(@NotNull Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages, int maxSize) {
        if (patchedUsages.size() == 0) return "";

        String patchStr = ConvertUsagesUtil.convertUsages(patchedUsages);
        if (maxSize > 0 && patchStr.getBytes().length > maxSize) {
            patchStr = ConvertUsagesUtil.cutPatchString(patchStr, maxSize);
        }

        return patchStr;
    }

    @NotNull
    public static Map<GroupDescriptor, Set<PatchedUsage>> getPatchedUsages(@NotNull Set<String> disabledGroups,
                                                                           @NotNull Project[] projects,
                                                                           @NotNull SentUsagesPersistence usagesPersistence) {
        Map<GroupDescriptor, Set<PatchedUsage>> usages = new LinkedHashMap<GroupDescriptor, Set<PatchedUsage>>();

        for (Project project : projects) {
            final Map<GroupDescriptor, Set<UsageDescriptor>> allUsages = getAllUsages(project, disabledGroups);
            final Map<GroupDescriptor, Set<UsageDescriptor>> sentUsages = filterDisabled(disabledGroups, usagesPersistence.getSentUsages());

            usages.putAll(getPatchedUsages(allUsages, sentUsages));
        }
        return usages;
    }

    @NotNull
    private static Map<GroupDescriptor, Set<UsageDescriptor>> filterDisabled(@NotNull Set<String> disabledGroups, @NotNull Map<GroupDescriptor, Set<UsageDescriptor>> usages) {
        Map<GroupDescriptor, Set<UsageDescriptor>> filtered = new LinkedHashMap<GroupDescriptor, Set<UsageDescriptor>>();

        for (Map.Entry<GroupDescriptor, Set<UsageDescriptor>> usage : usages.entrySet()) {
            if (!disabledGroups.contains(usage.getKey().getId())) {
                filtered.put(usage.getKey(), usage.getValue());
            }
        }
        return filtered;
    }

    @NotNull
    public static Map<GroupDescriptor, Set<PatchedUsage>> getPatchedUsages(@NotNull final Map<GroupDescriptor, Set<UsageDescriptor>> allUsages,
                                                                           @NotNull SentUsagesPersistence usagesPersistence) {
        return getPatchedUsages(allUsages, usagesPersistence.getSentUsages());
    }

    @NotNull
    public static Map<GroupDescriptor, Set<PatchedUsage>> getPatchedUsages(@NotNull final Map<GroupDescriptor, Set<UsageDescriptor>> allUsages, final Map<GroupDescriptor, Set<UsageDescriptor>> sentUsageMap) {
        Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = mapToPatchedUsagesMap(allUsages);

        for (Map.Entry<GroupDescriptor, Set<UsageDescriptor>> sentUsageEntry : sentUsageMap.entrySet()) {
            final GroupDescriptor sentUsageGroupDescriptor = sentUsageEntry.getKey();

            final Set<UsageDescriptor> sentUsages = sentUsageEntry.getValue();

            for (UsageDescriptor sentUsage : sentUsages) {
                final PatchedUsage descriptor = findDescriptor(patchedUsages, Pair.create(sentUsageGroupDescriptor, sentUsage.getKey()));
                if (descriptor == null) {
                    if (!patchedUsages.containsKey(sentUsageGroupDescriptor)) {
                        patchedUsages.put(sentUsageGroupDescriptor, new LinkedHashSet<PatchedUsage>());
                    }
                    patchedUsages.get(sentUsageGroupDescriptor).add(new PatchedUsage(sentUsage.getKey(), -sentUsage.getValue()));
                } else {
                    descriptor.subValue(sentUsage.getValue());
                }
            }

        }

        return packCollection(patchedUsages, new Condition<PatchedUsage>() {
            @Override
            public boolean value(PatchedUsage patchedUsage) {
                return patchedUsage.getDelta() != 0;
            }
        });
    }

    private static Map<GroupDescriptor, Set<PatchedUsage>> mapToPatchedUsagesMap(Map<GroupDescriptor, Set<UsageDescriptor>> allUsages) {
        Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages = new LinkedHashMap<GroupDescriptor, Set<PatchedUsage>>();
        for (Map.Entry<GroupDescriptor, Set<UsageDescriptor>> entry : allUsages.entrySet()) {
            patchedUsages.put(entry.getKey(), new HashSet<PatchedUsage>(ContainerUtil.map2Set(entry.getValue(), new Function<UsageDescriptor, PatchedUsage>() {
                @Override
                public PatchedUsage fun(UsageDescriptor usageDescriptor) {
                    return new PatchedUsage(usageDescriptor);
                }
            })));
        }
        return patchedUsages;
    }

    @NotNull
    private static Map<GroupDescriptor, Set<PatchedUsage>> packCollection(@NotNull Map<GroupDescriptor, Set<PatchedUsage>> patchedUsages, Condition<PatchedUsage> condition) {
        Map<GroupDescriptor, Set<PatchedUsage>> result = new LinkedHashMap<GroupDescriptor, Set<PatchedUsage>>();
        for (GroupDescriptor descriptor : patchedUsages.keySet()) {
            final Set<PatchedUsage> usages = packCollection(patchedUsages.get(descriptor), condition);
            if (usages.size() > 0) {
                result.put(descriptor, usages);
            }
        }

        return result;
    }

    @NotNull
    private static <T> Set<T> packCollection(@NotNull Collection<T> set, @NotNull Condition<T> condition) {
        final Set<T> result = new LinkedHashSet<T>();
        for (T t : set) {
            if (condition.value(t)) {
                result.add(t);
            }
        }
        return result;
    }

    @Nullable
    public static <T extends UsageDescriptor> T findDescriptor(@NotNull Map<GroupDescriptor, Set<T>> descriptors,
                                                               @NotNull final Pair<GroupDescriptor, String> id) {
        final Set<T> usages = descriptors.get(id.getFirst());
        if (usages == null) return null;

        return ContainerUtil.find(usages, new Condition<T>() {
            @Override
            public boolean value(T t) {
                return id.getSecond().equals(t.getKey());
            }
        });
    }

    @NotNull
    public static Map<GroupDescriptor, Set<UsageDescriptor>> getAllUsages(@Nullable Project project, @NotNull Set<String> disabledGroups) {
        Map<GroupDescriptor, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<GroupDescriptor, Set<UsageDescriptor>>();

        for (UsagesCollector usagesCollector : Extensions.getExtensions(UsagesCollector.EP_NAME)) {
            final GroupDescriptor groupDescriptor = usagesCollector.getGroupId();

            if (!disabledGroups.contains(groupDescriptor.getId())) {
              try {
                final Set<UsageDescriptor> usages = usagesCollector.getUsages(project);
                usageDescriptors.put(groupDescriptor, usages);
              }
              catch (CollectUsagesException e) {
                LOG.info(e);
              }
            }
        }

        return usageDescriptors;
    }

    private static final KeyedExtensionCollector<StatisticsService, String> COLLECTOR;

    static {
        COLLECTOR = new KeyedExtensionCollector<StatisticsService, String>("com.intellij.statisticsService");
    }

    public static StatisticsService getStatisticsService() {
        String key = ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getStatisticsServiceKey();

        StatisticsService service = key == null ? null : COLLECTOR.findSingle(key);

        if (service != null) {
            return service;
        }

        return new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(),
                                                         new StatisticsHttpClientSender(),
                                                         new StatisticsUploadAssistant());
    }

}
