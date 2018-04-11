// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(name = "FUSUsageTrigger")
public class FUSUsageTrigger implements UsagesCollectorConsumer, PersistentStateComponent<FUSUsageTrigger.State> {
  private final Project myProject;

  public FUSUsageTrigger(Project project) {
    myProject = project;
  }

  final static class State {
    @Property(surroundWithTag = false)
    @XCollection
    List<SessionInfo> sessions = ContainerUtil.newSmartList();
  }

  private State myState = new State();

  public void trigger(@NotNull Class<? extends FUSUsageTriggerCollector> fusClass,
                      @NotNull @NonNls String feature) {
    ProjectUsagesCollector collector = getUsageCollector(fusClass);
    if (collector != null) {
      doTrigger(collector.getGroupId(), feature);
    }
  }

  private void doTrigger(@NotNull String usageCollectorId,
                         @NotNull String feature) {
    SessionInfo sessionInfo = getOrCreateSessionInfo();
    UsagesCollectorInfo collectorInfo = sessionInfo.getUsageCollectorInfo(usageCollectorId);

    final Integer count = collectorInfo.counts.get(feature);
    if (count == null) {
      collectorInfo.counts.put(feature, 1);
    }
    else {
      collectorInfo.counts.put(feature, count + 1);
    }
  }

  public ProjectUsagesCollector getUsageCollector(@NotNull Class<? extends FUSUsageTriggerCollector> fusClass) {
    for (ProjectUsagesCollector collector : ProjectUsagesCollector.getExtensions(this)) {
      if (fusClass.equals(collector.getClass())) {
        return collector;
      }
    }
    return null;
  }

  public static FUSUsageTrigger getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FUSUsageTrigger.class);
  }

  @NotNull
  public Map<String, Integer> getData(@NotNull String usageCollectorId) {
    SessionInfo info = geExistingSessionInfo();
    if (info != null) {
      return info.getUsageCollectorInfo(usageCollectorId).counts;
    }
    return Collections.emptyMap();
  }

  @NotNull
  private SessionInfo getOrCreateSessionInfo() {
    SessionInfo info = geExistingSessionInfo();
    if (info != null) return info;
    SessionInfo sessionInfo = SessionInfo.create(FUSession.create(getProject()).getId());
    myState.sessions.add(sessionInfo);
    return sessionInfo;
  }

  @Nullable
  private SessionInfo geExistingSessionInfo() {
    FUSession session = FUSession.create(getProject());
    for (SessionInfo info : myState.sessions) {
      if (info.id == session.getId()) {
        return info;
      }
    }
    return null;
  }

  public State getState() {
    return myState;
  }

  public void loadState(@NotNull final State state) {
    myState = state;
  }

  public Project getProject() {
    return myProject;
  }

  @Tag("session")
  public static class SessionInfo {
    @Attribute("id")
    public int id;

    @Property(surroundWithTag = false)
    @XCollection
    List<UsagesCollectorInfo> collectors = ContainerUtil.newSmartList();

    @NotNull
    public UsagesCollectorInfo getUsageCollectorInfo(String id) {
      UsagesCollectorInfo collector = findUsageCollectorInfo(id);
      if (collector != null) return collector;

      UsagesCollectorInfo info = new UsagesCollectorInfo();
      info.id = id;
      collectors.add(info);
      return info;
    }

    @Nullable
    public UsagesCollectorInfo findUsageCollectorInfo(String id) {
      for (UsagesCollectorInfo collector : collectors) {
        if (id.equals(collector.id)) return collector;
      }
      return null;
    }

    public static SessionInfo create(int id) {
      SessionInfo info = new SessionInfo();
      info.id = id;
      return info;
    }
  }

  @Tag("usages-collector")
  public static class UsagesCollectorInfo {
    @Attribute("id")
    public String id;

    @Tag("counts")
    @MapAnnotation(surroundWithTag = false)
    public Map<String, Integer> counts = new HashMap<>();
  }
}
