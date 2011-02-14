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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.PatchedUsage;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.persistence.SentUsagesPersistence;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StatisticsUploadAssistant {

  public String getData() {
    return getData(Collections.<String>emptySet());
  }

  public static boolean showNotification() {
    return UsageStatisticsPersistenceComponent.getInstance().isShowNotification();
  }

  public static boolean isTimeToSend() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) return true; // todo remove

    return isTimeToSend(UsageStatisticsPersistenceComponent.getInstance());
  }

  public static boolean isTimeToSend(UsageStatisticsPersistenceComponent settings) {
    final long timeDelta = System.currentTimeMillis() - settings.getLastTimeSent();

    return Math.abs(timeDelta) > settings.getPeriod().getMillis();
  }

  public static boolean isSendAllowed() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) return true; // todo remove

    return isSendAllowed(UsageStatisticsPersistenceComponent.getInstance());
  }

  public static boolean isSendAllowed(final SentUsagesPersistence settings) {
    return settings != null && settings.isAllowed();
  }

  public static String getData(@NotNull Set<String> disabledGroups) {
    return getStringPatch(disabledGroups, ProjectManager.getInstance().getOpenProjects());
  }

  public static void persistSentPatch(@NotNull String patchStr) {
    persistSentPatch(patchStr, UsageStatisticsPersistenceComponent.getInstance());
  }

  public static void persistSentPatch(@NotNull String patchStr, @NotNull SentUsagesPersistence persistenceComponent) {
    Set<PatchedUsage> patchedUsages =
      ContainerUtil.map2Set(ConvertUsagesUtil.convertString(patchStr), new Function<UsageDescriptor, PatchedUsage>() {
        @Override
        public PatchedUsage fun(UsageDescriptor usageDescriptor) {
          return new PatchedUsage(usageDescriptor);
        }
      });

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
    final Set<PatchedUsage> patchedUsages = getPatchedUsages(disabledGroups, projects, usagesPersistence);

    return getStringPatch(patchedUsages, maxSize);
  }

  public static String getStringPatch(@NotNull Set<PatchedUsage> patchedUsages, int maxSize) {
    if (patchedUsages.size() == 0) return "";

    String patchStr = ConvertUsagesUtil.convertUsages(patchedUsages);
    if (maxSize > 0 && patchStr.getBytes().length > maxSize) {
      patchStr = ConvertUsagesUtil.cutPatchString(patchStr, maxSize);
    }

    return patchStr;
  }

  @NotNull
  public static Set<PatchedUsage> getPatchedUsages(@NotNull Set<String> disabledGroups,
                                                   @NotNull Project[] projects,
                                                   @NotNull SentUsagesPersistence usagesPersistence) {
    Set<PatchedUsage> usages = new HashSet<PatchedUsage>();

    for (Project project : projects) {
      final Set<UsageDescriptor> allUsages = getAllUsages(project, disabledGroups);
      final Set<UsageDescriptor> sentUsages = filterDisabled(disabledGroups, usagesPersistence.getSentUsages());

      usages.addAll(getPatchedUsages(allUsages, sentUsages));
    }
    return usages;
  }

  private static Set<UsageDescriptor> filterDisabled(@NotNull Set<String> disabledGroups, @NotNull Set<UsageDescriptor> usages) {
    Set<UsageDescriptor> filtered = new HashSet<UsageDescriptor>();

    for (UsageDescriptor usage : usages) {
      if (!disabledGroups.contains(usage.getGroup().getId())) {
        filtered.add(usage);
      }
    }
    return filtered;
  }

  @NotNull
  public static Set<PatchedUsage> getPatchedUsages(@NotNull final Set<UsageDescriptor> allUsages,
                                                   @NotNull SentUsagesPersistence usagesPersistence) {
    return getPatchedUsages(allUsages, usagesPersistence.getSentUsages());
  }

  @NotNull
  public static Set<PatchedUsage> getPatchedUsages(@NotNull final Set<UsageDescriptor> allUsages, final Set<UsageDescriptor> sentUsages) {
    final Set<PatchedUsage> patchedUsages = ContainerUtil.map2Set(allUsages, new Function<UsageDescriptor, PatchedUsage>() {
      @Override
      public PatchedUsage fun(UsageDescriptor usageDescriptor) {
        return new PatchedUsage(usageDescriptor);
      }
    });

    for (UsageDescriptor sentUsage : sentUsages) {
      final PatchedUsage descriptor = findDescriptor(patchedUsages, Pair.create(sentUsage.getGroup(), sentUsage.getKey()));
      if (descriptor == null) {
        patchedUsages.add(new PatchedUsage(sentUsage.getGroup(), sentUsage.getKey(), -sentUsage.getValue()));
      }
      else {
        descriptor.subValue(sentUsage.getValue());
      }
    }

    return packCollection(patchedUsages, new Condition<PatchedUsage>() {
      @Override
      public boolean value(PatchedUsage patchedUsage) {
        return patchedUsage.getDelta() != 0;
      }
    });
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
  public static <T extends UsageDescriptor> T findDescriptor(@NotNull Set<T> descriptors,
                                                             @NotNull final Pair<GroupDescriptor, String> id) {
    return ContainerUtil.find(descriptors, new Condition<T>() {
      @Override
      public boolean value(T t) {
        return id.getFirst().equals(t.getGroup()) && id.getSecond().equals(t.getKey());
      }
    });
  }

  @NotNull
  public static Set<UsageDescriptor> getAllUsages(@Nullable Project project, @NotNull Set<String> disabledGroups) {
    final Set<UsageDescriptor> usageDescriptors = new TreeSet<UsageDescriptor>();

    for (UsagesCollector usagesCollector : Extensions.getExtensions(UsagesCollector.EP_NAME)) {
      if (!disabledGroups.contains(usagesCollector.getGroupId())) {
        usageDescriptors.addAll(usagesCollector.getUsages(project));
      }
    }

    return usageDescriptors;
  }
}
