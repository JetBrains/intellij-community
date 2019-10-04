// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.*;

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
  public CancellablePromise<Set<MetricEvent>> getMetrics(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    final Set<MetricEvent> events = new HashSet<>();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager == null) {
      return Promises.resolvedCancellablePromise(Collections.emptySet());
    }
    final FileType[] registeredFileTypes = fileTypeManager.getRegisteredFileTypes();
    Collection<Promise<?>> promises = new ArrayList<>(registeredFileTypes.length);
    for (final FileType fileType : registeredFileTypes) {
      if (project.isDisposed()) {
        return Promises.resolvedCancellablePromise(Collections.emptySet());
      }
      promises.add(ReadAction.nonBlocking(() -> {
        IProjectStore stateStore = ProjectKt.getStateStore(project);
        FileTypeIndex.processFiles(fileType, file -> {
          ProgressManager.checkCanceled();
          //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
          if (!stateStore.isProjectFile(file)) {
            events.add(new MetricEvent("file.in.project", newFeatureUsageData(fileType)));
            return false;
          }
          return true;
        }, GlobalSearchScope.projectScope(project));
      }).cancelWith(indicator).expireWith(project).submit(NonUrgentExecutor.getInstance()));
    }
    return ((CancellablePromise<Set<MetricEvent>>)Promises.all(promises).then(o -> events));
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
