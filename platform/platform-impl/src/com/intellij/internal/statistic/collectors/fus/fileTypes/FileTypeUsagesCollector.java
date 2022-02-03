// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.project.ProjectKt;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// todo disable in guest (no file types)
public class FileTypeUsagesCollector extends ProjectUsagesCollector {
  private static final String DEFAULT_ID = "third.party";

  private final EventLogGroup GROUP = new EventLogGroup("file.types", 5);

  private final RoundedIntEventField COUNT = EventFields.RoundedInt("count");
  private final EventField<String> SCHEMA = EventFields.StringValidatedByCustomRule("schema", "file_type_schema");
  private final IntEventField PERCENT = EventFields.Int("percent");
  private final ObjectListEventField FILE_SCHEME_PERCENT = new ObjectListEventField("file_schema", SCHEMA, PERCENT);

  private final VarargEventId FILE_IN_PROJECT = GROUP.registerVarargEvent(
    "file.in.project",
    EventFields.PluginInfoFromInstance,
    EventFields.FileType,
    COUNT,
    FILE_SCHEME_PERCENT
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
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
        Ref<Integer> counter = new Ref<>(0);
        ConcurrentHashMap<String, Integer> schemas = new ConcurrentHashMap<>();
        FileTypeIndex.processFiles(fileType, file -> {
          ProgressManager.checkCanceled();
          //skip files from .idea directory otherwise 99% of projects would have XML and PLAIN_TEXT file types
          if (!stateStore.isProjectFile(file)) {
            counter.set(counter.get() + 1);
            final String schema = FileTypeUsageCounterCollector.findSchema(project, file);
            if (schema != null) {
              schemas.compute(schema, (k,v) -> 1 + (v == null ? 0 : v));
            }
          }
          return true;
        }, GlobalSearchScope.projectScope(project));

        Integer count = counter.get();
        if (count != 0) {
          List<EventPair<?>> eventPairs = new ArrayList<>(4);
          eventPairs.add(EventFields.PluginInfoFromInstance.with(fileType));
          eventPairs.add(EventFields.FileType.with(fileType));
          eventPairs.add(COUNT.with(count));
          if (!schemas.isEmpty()) {
            eventPairs.add(FILE_SCHEME_PERCENT.with(ContainerUtil.map(
              schemas.keySet(), schema -> new ObjectEventData(SCHEMA.with(schema), PERCENT.with((schemas.get(schema) * 100) / count)))));
          }
          events.add(FILE_IN_PROJECT.metric(eventPairs));
        }
      }).wrapProgress(indicator).expireWith(project).submit(NonUrgentExecutor.getInstance()));
    }
    return ((CancellablePromise<Set<MetricEvent>>)Promises.all(promises).then(o -> events));
  }

  public static String getSafeFileTypeName(@NotNull FileType fileType) {
    final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(fileType.getClass());
    return info.isDevelopedByJetBrains() ? fileType.getName() : DEFAULT_ID;
  }

  public static class ValidationRule extends CustomValidationRule {
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
