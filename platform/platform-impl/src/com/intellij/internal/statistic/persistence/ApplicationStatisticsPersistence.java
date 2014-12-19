package com.intellij.internal.statistic.persistence;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public abstract class ApplicationStatisticsPersistence {
  private final Map<GroupDescriptor, Map<String, Set<UsageDescriptor>>> myApplicationData = new THashMap<GroupDescriptor, Map<String, Set<UsageDescriptor>>>();

  public void persistUsages(@NotNull GroupDescriptor groupDescriptor, @NotNull Project project, @NotNull Set<UsageDescriptor> usageDescriptors) {
    if (!myApplicationData.containsKey(groupDescriptor)) {
      myApplicationData.put(groupDescriptor, new THashMap<String, Set<UsageDescriptor>>());
    }
    myApplicationData.get(groupDescriptor).put(project.getName(), usageDescriptors);
  }

  @NotNull
  public Map<String, Set<UsageDescriptor>> getApplicationData(@NotNull GroupDescriptor groupDescriptor) {
    if (!myApplicationData.containsKey(groupDescriptor)) {
      myApplicationData.put(groupDescriptor, new THashMap<String, Set<UsageDescriptor>>());
    }
    return myApplicationData.get(groupDescriptor);
  }

  @NotNull
  public Map<GroupDescriptor, Map<String, Set<UsageDescriptor>>> getApplicationData() {
    return myApplicationData;
  }
}
