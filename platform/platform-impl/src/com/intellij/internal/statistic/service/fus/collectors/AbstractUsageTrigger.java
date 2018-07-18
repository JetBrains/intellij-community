// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractUsageTrigger<T extends FeatureUsagesCollector> implements UsagesCollectorConsumer {
  private static final Logger LOG = Logger.getInstance("#" + AbstractUsageTrigger.class.getPackage().getName());
  
  final static class State {
    @Property(surroundWithTag = false)
    @XCollection
    List<SessionInfo> sessions = ContainerUtil.newSmartList();
  }

  private State myState = new State();

  public void trigger(@NotNull Class<? extends T> fusClass,
                      @NotNull @NonNls String feature) {
    FeatureUsagesCollector collector = findCollector(fusClass);
    if (collector != null) {
      doTrigger(collector.getGroupId(), feature);
    }
    else {
      LOG.warn("Cannot find collector `" + fusClass + "`. Make sure it's registered");
    }
  }

  protected abstract FeatureUsagesCollector findCollector(@NotNull Class<? extends T> fusClass);

  protected void doTrigger(@NotNull String usageCollectorId,
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
    SessionInfo sessionInfo = SessionInfo.create(getFUSession().getId());
    myState.sessions.add(sessionInfo);
    return sessionInfo;
  }

  protected abstract FUSession getFUSession();

  @Nullable
  private SessionInfo geExistingSessionInfo() {
    FUSession session = getFUSession();
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
