package com.intellij.internal.statistic.persistence;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public abstract class ApplicationStatisticsPersistence {
  private Map<GroupDescriptor, Map<String, Set<UsageDescriptor>>> myApplicationData = new HashMap<GroupDescriptor, Map<String, Set<UsageDescriptor>>>();

  public ApplicationStatisticsPersistence() {
  }

  public void persistFrameworks(@NotNull GroupDescriptor groupDescriptor, @NotNull Project project, @NotNull Set<UsageDescriptor> frameworks) {
      if (!myApplicationData.containsKey(groupDescriptor)) {
          myApplicationData.put(groupDescriptor, new HashMap<String, Set<UsageDescriptor>>());
      }
      myApplicationData.get(groupDescriptor).put(project.getName(), frameworks);
  }

  @NotNull
  public Map<String, Set<UsageDescriptor>> getApplicationData(@NotNull GroupDescriptor groupDescriptor) {
      final Map<String, Set<UsageDescriptor>> map = myApplicationData.get(groupDescriptor);

      return map == null ? new HashMap<String, Set<UsageDescriptor>>(): map;
  }

  @NotNull
  public Map<GroupDescriptor, Map<String, Set<UsageDescriptor>>> getApplicationData() {
      return myApplicationData;
  }

}
