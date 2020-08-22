// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class FacetTypeUsageCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public String getGroupId() {
    return "module.facets";
  }

  @Override
  public int getVersion() {
    return 4;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    final Set<String> facets = new HashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        facets.add(facet.getType().getStringId());
      }
    }
    return ContainerUtil.map2Set(
      facets, facet -> new MetricEvent("module.with.facet", new FeatureUsageData().addData("facet", facet))
    );
  }

  public static class FacetTypeUtilValidator extends CustomValidationRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "facets_type".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if ("invalid".equals(data) || isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      final FacetType facet = findFacetById(data);
      if (facet == null) {
        return ValidationResultType.REJECTED;
      }
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(facet.getClass());
      context.setPluginInfo(info);
      return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }

    @Nullable
    private static FacetType findFacetById(@NotNull String data) {
      final FacetType[] facets = FacetType.EP_NAME.getExtensions();
      for (FacetType facet : facets) {
        if (StringUtil.equals(facet.getStringId(), data)) {
          return facet;
        }
      }
      return null;
    }
  }
}
