// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomUtilsWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


public class RegistryApplicationUsagesCollector extends ApplicationUsagesCollector {

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getChangedValuesUsages();
  }

  @NotNull
  static Set<UsageDescriptor> getChangedValuesUsages() {
    Set<UsageDescriptor> registry = Registry.getAll().stream()
      .filter(key -> key.isChangedFromDefault())
      .map(key -> new UsageDescriptor(key.getKey()))
      .collect(Collectors.toSet());

    Set<UsageDescriptor> experiments = Arrays.stream(Experiments.EP_NAME.getExtensions())
      .filter(f -> Experiments.isFeatureEnabled(f.id))
      .map(f -> new UsageDescriptor(f.id))
      .collect(Collectors.toSet());

    HashSet<UsageDescriptor> result = new HashSet<>(registry);
    result.addAll(experiments);
    return result;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "platform.registry.application";
  }

  public static class RegistryUtilValidator extends CustomUtilsWhiteListRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "registry_key".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      final ExperimentalFeature feature = findFeatureById(data);
      if (feature != null) {
        final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(feature.getClass());
        if (StringUtil.equals(data, context.eventId)) {
          context.setPluginInfo(info);
        }
        return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
      }


      final RegistryValue value = Registry.get(data);
      return !value.isContributedByThirdPartyPlugin() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
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
