// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "FUSProjectUsageTrigger",
  storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED))
final public class FUSProjectUsageTrigger extends AbstractUsageTrigger<ProjectUsageTriggerCollector> implements PersistentStateComponent<AbstractUsageTrigger.State> {
  private final Project myProject;

  public FUSProjectUsageTrigger(Project project) {
    myProject = project;
  }

  public static FUSProjectUsageTrigger getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FUSProjectUsageTrigger.class);
  }

  @Override
  protected FeatureUsagesCollector findCollector(@NotNull Class<? extends ProjectUsageTriggerCollector> fusClass) {
    for (ProjectUsagesCollector collector : ProjectUsagesCollector.getExtensions(this)) {
      if (fusClass.equals(collector.getClass())) {
        return collector;
      }
    }
    return null;
  }

  @Override
  protected FUSession getFUSession() {
    return FUSession.create(getProject());
  }

  public Project getProject() {
    return myProject;
  }
}
