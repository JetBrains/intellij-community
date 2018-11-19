// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@State(name = "FUSProjectUsageTrigger", storages = {
  @Storage(value = StoragePathMacros.CACHE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
})
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
  protected Map<String, Object> createEventLogData(@Nullable FUSUsageContext context) {
    return StatisticsUtilKt.createData(myProject, context);
  }

  @Override
  protected FUSession getFUSession() {
    return FUSession.create(getProject());
  }

  public Project getProject() {
    return myProject;
  }
}
