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
package com.intellij.statistic;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.statistic.beans.GroupDescriptor;
import com.intellij.statistic.beans.PatchedUsage;
import com.intellij.statistic.beans.UsageDescriptor;
import com.intellij.statistic.persistence.SentUsagesPersistence;
import com.intellij.statistic.persistence.SentUsagesPersistenceComponent;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StatisticsUploadAssistant {

  private static final Character GROUP_SEPARATOR = ':';
  private static final Character GROUPS_SEPARATOR = ';';
  private static final Character GROUP_VALUE_SEPARATOR = ',';

  private StatisticsUploadAssistant() {
  }

  @NotNull
  public static String getStringPatch(@Nullable Project project) {
    return getStringPatch(project, 0);
  }

  @NotNull
  public static String getStringPatch(@Nullable Project project, int maxSize) {
    return getStringPatch(project, SentUsagesPersistenceComponent.getInstance(), maxSize);
  }

  @NotNull
  public static String getStringPatch(@Nullable Project project, @NotNull SentUsagesPersistence usagesPersistence, int maxSize) {
    final Set<PatchedUsage> patchedUsages = getPatchedUsages(project, usagesPersistence);

    return getStringPatch(patchedUsages, usagesPersistence, maxSize);
  }

  @NotNull
  public static String getStringPatch(@NotNull Set<PatchedUsage> patchedUsages,
                                      @NotNull SentUsagesPersistence usagesPersistence,
                                      int maxSize) {
    if (patchedUsages.size() == 0) return "";

    String patchStr = convertUsages(patchedUsages);
    if (maxSize > 0 && patchStr.getBytes().length > maxSize) {
      patchStr = cutPatchString(patchStr, maxSize);

      patchedUsages = ContainerUtil.map2Set(convertString(patchStr), new Function<UsageDescriptor, PatchedUsage>() {
        @Override
        public PatchedUsage fun(UsageDescriptor usageDescriptor) {
          return new PatchedUsage(usageDescriptor);
        }
      });
    }

    if (patchedUsages.size() > 0) usagesPersistence.persistPatch(patchedUsages);

    return patchStr;
  }

  @NotNull
  public static Set<PatchedUsage> getPatchedUsages(@Nullable Project project, @NotNull SentUsagesPersistence usagesPersistence) {
     return getPatchedUsages(getAllUsages(project), usagesPersistence.getSentUsages());
  }

  @NotNull
  public static Set<PatchedUsage> getPatchedUsages(@NotNull final Set<UsageDescriptor> allUsages, @NotNull SentUsagesPersistence usagesPersistence) {
     return getPatchedUsages(allUsages, usagesPersistence.getSentUsages());
  }

  @NotNull
  public static Set<PatchedUsage> getPatchedUsages(@NotNull final Set<UsageDescriptor> allUsages, final Set<UsageDescriptor> sentUsages ) {
    final Set<PatchedUsage> patchedUsages = ContainerUtil.map2Set(allUsages, new Function<UsageDescriptor, PatchedUsage>() {
      @Override
      public PatchedUsage fun(UsageDescriptor usageDescriptor) {
        return new PatchedUsage(usageDescriptor);
      }
    });

    for (UsageDescriptor sentUsage : sentUsages) {
      final PatchedUsage descriptor = findDescriptor(patchedUsages, sentUsage.getId());
      if (descriptor == null) {
        patchedUsages.add(new PatchedUsage(sentUsage.getGroup(), sentUsage.getKey(), - sentUsage.getValue()));
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

  @NotNull
  private static String cutPatchString(@NotNull String patchStr, int maxSize) {
    for (int i = maxSize - 1; i >= 0; i--) {
      final char c = patchStr.charAt(i);
      if (c == GROUPS_SEPARATOR || c == GROUP_VALUE_SEPARATOR) {
        return patchStr.substring(0, i);
      }
    }
    return "";
  }

  @Nullable
  public static <T extends UsageDescriptor> T findDescriptor(@NotNull Set<T> descriptors,
                                                             @NotNull final Pair<GroupDescriptor, String> id) {
    return ContainerUtil.find(descriptors, new Condition<T>() {
      @Override
      public boolean value(T t) {
        return id.equals(t.getId());
      }
    });
  }

  @NotNull
  public static Set<UsageDescriptor> getAllUsages(@Nullable Project project) {
    final Set<UsageDescriptor> usageDescriptors = new TreeSet<UsageDescriptor>();

    for (UsagesCollector usagesCollector : Extensions.getExtensions(UsagesCollector.EP_NAME)) {
      usageDescriptors.addAll(usagesCollector.getUsages(project));
    }

    return usageDescriptors;
  }

  @NotNull
  public static String convertUsages(@NotNull Set<? extends UsageDescriptor> descriptors) {
    final Map<GroupDescriptor, Set<UsageDescriptor>> descriptorGroups = groupDescriptors(descriptors);

    return convertUsages(descriptorGroups);
  }

  @NotNull
  public static Map<GroupDescriptor, Set<UsageDescriptor>> groupDescriptors(@NotNull Set<? extends UsageDescriptor> descriptors) {
    final SortedMap<GroupDescriptor, Set<UsageDescriptor>> map = new TreeMap<GroupDescriptor, Set<UsageDescriptor>>(new Comparator<GroupDescriptor>() {
      @Override
      public int compare(GroupDescriptor g1, GroupDescriptor g2) {
        final int priority = (int)(g2.getPriority() - g1.getPriority());
        return priority == 0 ? g1.getId().compareTo(g2.getId()) : priority;
      }
    });

    for (UsageDescriptor descriptor : descriptors) {
      final GroupDescriptor group = descriptor.getGroup();
      if (!map.containsKey(group)) {
        map.put(group, new HashSet<UsageDescriptor>());
      }
      map.get(group).add(descriptor);
    }
    return map;
  }

  @NotNull
  private static String convertUsages(@NotNull Map<GroupDescriptor, Set<UsageDescriptor>> map) {
    StringBuffer buffer = new StringBuffer();
    for (Map.Entry<GroupDescriptor, Set<UsageDescriptor>> entry : map.entrySet()) {
      buffer.append(entry.getKey().getId());
      buffer.append(GROUP_SEPARATOR);
      buffer.append(convertValueMap(entry.getValue()));
      buffer.append(GROUPS_SEPARATOR);
    }

    return buffer.toString();
  }

  @NotNull
  public static Set<UsageDescriptor> convertString(@NotNull String usages) {
    Set<UsageDescriptor> descriptors = new LinkedHashSet<UsageDescriptor>();
    for (String groupStr : StringUtil.split(usages, GROUPS_SEPARATOR.toString())) {
      if (!StringUtil.isEmptyOrSpaces(groupStr)) {
        final Pair<String, String> group = getPair(groupStr, GROUP_SEPARATOR.toString());
        if (group != null) {
          descriptors.addAll(convertValueString(GroupDescriptor.create(group.getFirst()), group.getSecond()));
        }
      }
    }
    return descriptors;
  }

  @NotNull
  public static String convertValueMap(@NotNull Set<UsageDescriptor> descriptors) {
    final StringBuffer buffer = new StringBuffer();
    for (UsageDescriptor usageDescriptor : descriptors) {
      buffer.append(usageDescriptor.getKey());
      buffer.append("=");
      buffer.append(usageDescriptor.getValue());
      buffer.append(GROUP_VALUE_SEPARATOR);
    }
    buffer.deleteCharAt(buffer.length() - 1);

    return buffer.toString();
  }

  @NotNull
  public static Set<UsageDescriptor> convertValueString(@NotNull GroupDescriptor groupId, String valueData) {
    final Set<UsageDescriptor> descriptors = new LinkedHashSet<UsageDescriptor>();
    for (String value : StringUtil.split(valueData, GROUP_VALUE_SEPARATOR.toString())) {
      if (!StringUtil.isEmptyOrSpaces(value)) {
        final Pair<String, String> pair = getPair(value, "=");
        if (pair != null) {
          final String count = pair.getSecond();
          if (!StringUtil.isEmptyOrSpaces(count)) {
            try {
              final int i = Integer.parseInt(count);
              descriptors.add(new UsageDescriptor(groupId, pair.getFirst(), i));
            } catch (NumberFormatException ignored) {}
          }
        }
      }
    }

    return descriptors;
  }

  @Nullable
  private static Pair<String, String> getPair(@NotNull String str, @NotNull String separator) {
    final int i = str.indexOf(separator);
    if (i > 0 && i < str.length() - 1) {
      String key = str.substring(0, i).trim();
      String value = str.substring(i + 1).trim();
      if (!StringUtil.isEmptyOrSpaces(key) && !StringUtil.isEmptyOrSpaces(value)) {
        return Pair.create(key, value);
      }
    }
    return null;
  }
}
