package com.intellij.internal.statistic.ideSettings;

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class IdeSettingsUsagesCollector extends AbstractApplicationUsagesCollector {

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    Set<UsageDescriptor> usageDescriptors = new HashSet<UsageDescriptor>();
    for (Pair<Object, IdeSettingsDescriptor> provider : getProviders(project, true)) {
      usageDescriptors.addAll(IdeSettingsStatisticsUtils.getUsages(provider.second, provider.first));
    }
    return usageDescriptors;
  }

  @NotNull
  private static Set<Pair<Object, IdeSettingsDescriptor>> getProviders(@Nullable Project project, boolean projectComponent) {
    Set<Pair<Object, IdeSettingsDescriptor>> pairs = new HashSet<Pair<Object, IdeSettingsDescriptor>>();
    for (IdeSettingsDescriptor descriptor : IdeSettingsStatisticsService.getInstance().getSettingDescriptors()) {
      final Pair<Object, IdeSettingsDescriptor> pair = getProvider(project, descriptor, projectComponent);
      if (pair != null) {
        pairs.add(pair);
      }
    }
    return pairs;
  }

  @Nullable
  private static Pair<Object, IdeSettingsDescriptor> getProvider(@Nullable Project project,
                                                                 @NotNull IdeSettingsDescriptor descriptor,
                                                                 boolean projectComponent) {
    Object applicationProvider = IdeSettingsStatisticsUtils.getApplicationProvider(descriptor.myProviderName);

    if (applicationProvider != null) {
      return projectComponent ? null : Pair.create(applicationProvider, descriptor);
    }

    if (project != null) {
      final Object projectProvider = IdeSettingsStatisticsUtils.getProjectProvider(project, descriptor.myProviderName);
      if (projectProvider != null) {
        return projectComponent ? Pair.create(projectProvider, descriptor) : null;
      }
    }
    return null;
  }


  @NotNull
  @Override
  public Set<UsageDescriptor> getApplicationUsages() {
    Set<UsageDescriptor> applicationUsageDescriptors = new HashSet<UsageDescriptor>();

    applicationUsageDescriptors.addAll(super.getApplicationUsages());

    for (Pair<Object, IdeSettingsDescriptor> provider : getProviders(null, false)) {
      applicationUsageDescriptors.addAll(IdeSettingsStatisticsUtils.getUsages(provider.second, provider.first));
    }

    return applicationUsageDescriptors;
  }

  @NotNull
  public GroupDescriptor getGroupId() {
    return IdeSettingsStatisticsUtils.GROUP;
  }
}
