// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractUsageTrigger<T extends FeatureUsagesCollector> implements UsagesCollectorConsumer {
  private static final Logger LOG = Logger.getInstance("#" + AbstractUsageTrigger.class.getPackage().getName());

  final static class State {
    @Property(surroundWithTag = false)
    @XCollection
    List<SessionInfo> sessions = ContainerUtil.newSmartList();
  }

  private State myState = new State();

  public void trigger(@NotNull Class<? extends T> fusClass, @NotNull @NonNls String feature) {
    trigger(fusClass, feature, null);
  }

  public void trigger(@NotNull Class<? extends T> fusClass,
                      @NotNull @NonNls String feature,
                      @Nullable FUSUsageContext context) {
    FeatureUsagesCollector collector = findCollector(fusClass);
    if (collector != null) {
      doTrigger(collector.getGroupId(), feature, context);
    } else {
      LOG.warn("Cannot find collector `" + fusClass + "`. Make sure it's registered");
    }
  }

  protected abstract FeatureUsagesCollector findCollector(@NotNull Class<? extends T> fusClass);

  protected abstract Map<String, Object> createEventLogData(@Nullable FUSUsageContext context);

  protected void doTrigger(@NotNull String usageCollectorId,
                           @NotNull String feature,
                           @Nullable FUSUsageContext context) {
    FeatureUsageLogger.INSTANCE.log(usageCollectorId, feature, createEventLogData(context));

    SessionInfo sessionInfo = getOrCreateSessionInfo();
    UsagesCollectorInfo collectorInfo = sessionInfo.getUsageCollectorInfo(usageCollectorId);

    migrateLegacyData(collectorInfo);

    FUsageInfo usage = findUsageInfo(collectorInfo.usages.toArray(new FUsageInfo[0]), feature, context);
    if (usage == null) {
      collectorInfo.usages.add(FUsageInfo.create(feature, 1, context));
    }
    else {
      usage.count += 1;
    }
  }

  private static void migrateLegacyData(@NotNull UsagesCollectorInfo info) {
    if (info.counts.size() > 0) {
      for (Map.Entry<String, Integer> entry : info.counts.entrySet()) {
        info.usages.add(FUsageInfo.create(entry.getKey(), entry.getValue(), null));
      }
      info.counts.clear();
    }
  }

  @Nullable
  private static FUsageInfo findUsageInfo(@NotNull FUsageInfo[] usages, @NotNull String feature, @Nullable FUSUsageContext context) {
    for (FUsageInfo usage : usages) {
      if (usage.id.equals(feature) && equalContexts(context, usage.context)) return usage;
    }
    return null;
  }

  private static boolean equalContexts(@Nullable FUSUsageContext context, @Nullable Map<String, String> contextData) {
    if (context == null && (contextData == null || contextData.isEmpty())) return true;
    return context != null && Objects.equals(contextData, context.getData());
  }

  @NotNull
  public Set<UsageDescriptor> getData(@NotNull String usageCollectorId) {
    SessionInfo info = geExistingSessionInfo();
    if (info != null) {
      return info.getUsageCollectorInfo(usageCollectorId).usages.stream()
        .map(usage -> new UsageDescriptor(usage.id, usage.count, (usage.context != null && usage.context.size() > 0) ?
                                                                 FUSUsageContext.create(usage.contextValues()) : null))
        .collect(Collectors.toSet());
    }
    return Collections.emptySet();
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
    for (SessionInfo info : myState.sessions.toArray(new SessionInfo[0])) {
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
      for (UsagesCollectorInfo collector : collectors.toArray(new UsagesCollectorInfo[0])) {
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

    @Property(surroundWithTag = false)
    @XCollection
    Set<FUsageInfo> usages = ContainerUtil.newLinkedHashSet();
  }

  @Tag("usage")
  public static class FUsageInfo {
    @Attribute("id")
    public String id;

    @Attribute("value")
    public Integer count;

    @Tag("context")
    @MapAnnotation(surroundWithTag = false)
    public Map<String, String> context = ContainerUtil.newLinkedHashMap();

    private static FUsageInfo create(@NotNull String feature, int value, @Nullable FUSUsageContext context) {
      FUsageInfo usage = new FUsageInfo();
      usage.id = feature;
      usage.count = value;
      usage.context = context != null ? context.getData() : ContainerUtil.newLinkedHashMap();
      return usage;
    }

    public  String[] contextValues() {
      return ArrayUtil.toStringArray(context.values());
    }
  }
}
