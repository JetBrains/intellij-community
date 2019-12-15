// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@State(name = "FUSProjectUsageTrigger", storages = {
  @Storage(value = StoragePathMacros.CACHE_FILE, deprecated = true),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true),
  @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, deprecated = true),
})
final public class LegacyFUSProjectUsageTrigger implements PersistentStateComponent<LegacyFUSProjectUsageTrigger.State> {
  private final State myState = new State();

  final static class State {
    @Transient
    List<String> sessions = new SmartList<>();
  }

  public static void cleanup(@NotNull Project project) {
    ServiceManager.getService(project, LegacyFUSProjectUsageTrigger.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final State state) {
  }
}
