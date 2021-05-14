// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.*;

public class RegistryApplicationUsagesCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("platform.registry", 3);
  private static final StringEventField REGISTRY_KEY = EventFields.StringValidatedByCustomRule("id", "registry_key");

  private static final VarargEventId REGISTRY = GROUP.registerVarargEvent("registry", REGISTRY_KEY, EventFields.PluginInfo);
  private static final VarargEventId EXPERIMENT = GROUP.registerVarargEvent("experiment", REGISTRY_KEY, EventFields.PluginInfo);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    return getChangedValuesUsages();
  }

  @NotNull
  static Set<MetricEvent> getChangedValuesUsages() {
    final Set<MetricEvent> registry = Registry.getAll().stream()
      .filter(key -> key.isChangedFromDefault())
      .map(key -> REGISTRY.metric(REGISTRY_KEY.with(key.getKey())))
      .collect(Collectors.toSet());

    final Set<MetricEvent> experiments = Experiments.EP_NAME.extensions()
      .filter(f -> Experiments.getInstance().isFeatureEnabled(f.id))
      .map(f -> EXPERIMENT.metric(REGISTRY_KEY.with(f.id)))
      .collect(Collectors.toSet());

    final Set<MetricEvent> result = new HashSet<>(registry);
    result.addAll(experiments);
    return result;
  }

  public static class RegistryUtilValidator extends CustomValidationRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "registry_key".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      final ExperimentalFeature feature = findFeatureById(data);
      if (feature != null) {
        final PluginInfo info = getPluginInfo(feature.getClass());
        context.setPayload(PLUGIN_INFO, info);
        return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }

      PluginInfo info = getPluginInfoByRegistry(Registry.get(data));
      context.setPayload(PLUGIN_INFO, info);
      return info.isSafeToReport() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }

    @NotNull
    private static PluginInfo getPluginInfoByRegistry(@NotNull RegistryValue value) {
      String pluginId = value.getPluginId();
      return pluginId != null ? getPluginInfoById(PluginId.getId(pluginId)) : getPlatformPlugin();
    }

    @Nullable
    private static ExperimentalFeature findFeatureById(@NotNull String featureId) {
      for (ExperimentalFeature feature : Experiments.EP_NAME.getExtensions()) {
        if (StringUtil.equals(feature.id, featureId)) {
          return feature;
        }
      }
      return null;
    }
  }
}
