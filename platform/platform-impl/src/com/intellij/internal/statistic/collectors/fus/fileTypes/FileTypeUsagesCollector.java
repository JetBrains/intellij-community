// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FileTypeUsagesCollector extends ProjectUsagesCollector {
  private static final String DEFAULT_ID = "third.party";

  @NotNull
  @Override
  public String getGroupId() {
    return "file.types";
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    return getDescriptors(project);
  }

  @NotNull
  public static Set<MetricEvent> getDescriptors(@NotNull Project project) {
    final Set<MetricEvent> events = new HashSet<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager == null) {
      return Collections.emptySet();
    }
    final FileType[] registeredFileTypes = fileTypeManager.getRegisteredFileTypes();
    for (final FileType fileType : registeredFileTypes) {
      if (project.isDisposed()) {
        return Collections.emptySet();
      }

      ApplicationManager.getApplication().runReadAction(() -> {
        FileTypeIndex.processFiles(fileType, file -> {
          //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
          if (!ProjectKt.getStateStore(project).isProjectFile(file)) {
            events.add(new MetricEvent("file.in.project", newFeatureUsageData(fileType)));
            return false;
          }
          return true;
        }, GlobalSearchScope.projectScope(project));
      });
    }
    return events;
  }

  @NotNull
  public static FeatureUsageData newFeatureUsageData(@NotNull FileType type) {
    final FeatureUsageData data = new FeatureUsageData();
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(type.getClass());
    data.addPluginInfo(info);
    data.addData("file_type", info.isDevelopedByJetBrains() ? type.getName() : DEFAULT_ID);
    return data;
  }

  public static class ValidationRule extends CustomWhiteListRule {
    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "file_type".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      final FileType fileType = FileTypeManager.getInstance().findFileTypeByName(data);
      if (fileType == null || !StringUtil.equals(fileType.getName(), data)) {
        return ValidationResultType.REJECTED;
      }

      final boolean isDevelopedByJB = PluginInfoDetectorKt.getPluginInfo(fileType.getClass()).isDevelopedByJetBrains();
      return isDevelopedByJB ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }
  }
}
