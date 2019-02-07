// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.internal.statistic.eventLog.FeatureUsageDataBuilder;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(name = "IntentionsCollector", storages = @Storage(
  value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true)
)
public class IntentionsCollector implements PersistentStateComponent<IntentionsCollector.State> {
  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("intentions", 1);
  private static final String DEFAULT_ID = "third.party.intention";

  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
  }

  private static final List<String> PREFIXES_TO_STRIP = Arrays.asList("com.intellij.codeInsight.",
                                                                      "com.intellij.");

  public void record(@NotNull IntentionAction action, @NotNull Language language) {
    final Class<?> clazz = getOriginalHandlerClass(action);
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(clazz);

    final Map<String, Object> data = new FeatureUsageDataBuilder().
      addFeatureContext(FUSUsageContext.OS_CONTEXT).
      addPluginInfo(info).
      addLanguage(language).
      createData();

    final String id = info.isSafeToReport() ? toReportedId(clazz) : DEFAULT_ID;
    FeatureUsageLogger.INSTANCE.log(GROUP, id, data);
  }

  @NotNull
  private static Class getOriginalHandlerClass(@NotNull IntentionAction action) {
    Object handler = action;
    if (action instanceof IntentionActionDelegate) {
      IntentionAction delegate = ((IntentionActionDelegate)action).getDelegate();
      if (delegate != action) {
        return getOriginalHandlerClass(delegate);
      }
    }
    else if (action instanceof QuickFixWrapper) {
      LocalQuickFix fix = ((QuickFixWrapper)action).getFix();
      if (fix != action) {
        handler = fix;
      }
    }
    return handler.getClass();
  }

  @NotNull
  private static String toReportedId(Class<?> clazz) {
    String fqn = clazz.getName();
    for (String prefix : PREFIXES_TO_STRIP) {
      fqn = StringUtil.trimStart(fqn, prefix);
    }
    return fqn;
  }

  public static IntentionsCollector getInstance() {
    return ServiceManager.getService(IntentionsCollector.class);
  }

  public final static class State {
    @Tag("Intentions")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "name", valueAttributeName = "count")
    public Map<String, Integer> myIntentions = new HashMap<>();
  }
}

