package com.intellij.internal.statistic.persistence;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.openapi.project.Project;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public abstract class ApplicationStatisticsPersistence {
  private final Map<GroupDescriptor, Map<String, CollectedUsages>> myApplicationData = new THashMap<>();

  public void persistUsages(@NotNull GroupDescriptor groupDescriptor, @NotNull Project project, @NotNull CollectedUsages usageDescriptors) {
    if (!myApplicationData.containsKey(groupDescriptor)) {
      myApplicationData.put(groupDescriptor, new THashMap<>());
    }
    myApplicationData.get(groupDescriptor).put(project.getName(), usageDescriptors);
  }

  @NotNull
  public Map<String, CollectedUsages> getApplicationData(@NotNull GroupDescriptor groupDescriptor) {
    if (!myApplicationData.containsKey(groupDescriptor)) {
      myApplicationData.put(groupDescriptor, new THashMap<>());
    }
    return myApplicationData.get(groupDescriptor);
  }

  @NotNull
  public Map<GroupDescriptor, Map<String, CollectedUsages>> getApplicationData() {
    return myApplicationData;
  }
}
