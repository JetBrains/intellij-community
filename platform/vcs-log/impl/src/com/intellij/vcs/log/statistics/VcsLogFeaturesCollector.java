// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.statistics;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter;
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.ui.table.column.VcsLogDefaultColumn;
import com.intellij.vcs.log.util.GraphOptionsUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addBoolIfDiffers;
import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addIfDiffers;
import static com.intellij.vcs.log.impl.CommonUiProperties.*;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.AbstractVcsLogUi.LOG_HIGHLIGHTER_FACTORY_EP;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.getColumnsOrder;
import static com.intellij.vcs.log.ui.table.column.VcsLogDefaultColumnKt.getDefaultDynamicColumns;

@ApiStatus.Internal
public @NonNls class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("vcs.log.ui", 8);
  private static final EventId UI_INITIALIZED = GROUP.registerEvent("uiInitialized");
  private static final EventId MAIN_UI_INITIALIZED = GROUP.registerEvent("mainUiInitialized");
  private static final VarargEventId DETAILS = GROUP.registerVarargEvent("details", EventFields.Enabled);
  private static final VarargEventId DIFF_PREVIEW = GROUP.registerVarargEvent("diffPreview", EventFields.Enabled);
  private static final VarargEventId DIFF_PREVIEW_ON_THE_BOTTOM = GROUP.registerVarargEvent("diffPreviewOnTheBottom", EventFields.Enabled);
  private static final VarargEventId PARENT_CHANGES = GROUP.registerVarargEvent("parentChanges", EventFields.Enabled);
  private static final VarargEventId ONLY_AFFECTED_CHANGES = GROUP.registerVarargEvent("onlyAffectedChanges", EventFields.Enabled);
  private static final VarargEventId LONG_EDGES = GROUP.registerVarargEvent("long.edges", EventFields.Enabled);
  private static final EnumEventField<PermanentGraph.SortType> SORT_TYPE_FIELD =
    EventFields.Enum("value", PermanentGraph.SortType.class);
  private static final StringEventField GRAPH_OPTIONS_TYPE_FIELD =
    EventFields.String("value", GraphOptionsUtil.getOptionKindNames());
  private static final VarargEventId SORT = GROUP.registerVarargEvent("sort", EventFields.Enabled, SORT_TYPE_FIELD);
  private static final VarargEventId GRAPH_OPTIONS_TYPE =
    GROUP.registerVarargEvent("graphOptionsType", EventFields.Enabled, GRAPH_OPTIONS_TYPE_FIELD);
  private static final VarargEventId ROOTS = GROUP.registerVarargEvent("roots", EventFields.Enabled);
  private static final VarargEventId LABELS_COMPACT = GROUP.registerVarargEvent("labels.compact", EventFields.Enabled);
  private static final VarargEventId LABELS_SHOW_TAG_NAMES = GROUP.registerVarargEvent("labels.showTagNames", EventFields.Enabled);
  private static final VarargEventId LABELS_ON_THE_LEFT = GROUP.registerVarargEvent("labels.onTheLeft", EventFields.Enabled);
  private static final VarargEventId SHOW_COMMIT_DATE = GROUP.registerVarargEvent("showCommitDate", EventFields.Enabled);
  private static final VarargEventId TEXT_FILTER_REGEX = GROUP.registerVarargEvent("textFilter.regex", EventFields.Enabled);
  private static final VarargEventId TEXT_FILTER_MATCH_CASE = GROUP.registerVarargEvent("textFilter.matchCase", EventFields.Enabled);
  public static final String THIRD_PARTY = "THIRD_PARTY";
  private static final StringEventField LOG_HIGHLIGHTER_ID_FIELD =
    EventFields.String("id", List.of(VcsLogCommitsHighlighter.Factory.ID,
                                     MergeCommitsHighlighter.Factory.ID,
                                     CurrentBranchHighlighter.Factory.ID,
                                     THIRD_PARTY));
  private static final VarargEventId HIGHLIGHTER = GROUP.registerVarargEvent("highlighter", EventFields.Enabled, LOG_HIGHLIGHTER_ID_FIELD);
  private static final StringEventField FILTER_NAME =
    EventFields.String("name", ContainerUtil.map(VcsLogFilterCollection.STANDARD_KEYS, it -> it.getName()));
  private static final VarargEventId FILTER = GROUP.registerVarargEvent("filter", EventFields.Enabled, FILTER_NAME);
  private static final StringEventField COLUMN_NAME =
    EventFields.String("name", ContainerUtil.map(getDefaultDynamicColumns(), it -> it.getStableName()));
  private static final VarargEventId COLUMN = GROUP.registerVarargEvent("column", EventFields.Enabled, COLUMN_NAME);
  private static final VarargEventId ADDITIONAL_TOOL_WINDOW_TABS = GROUP.registerVarargEvent("additionalTabs.ToolWindow", EventFields.Count);
  private static final VarargEventId ADDITIONAL_EDITOR_TABS = GROUP.registerVarargEvent("additionalTabs.Editor", EventFields.Count);

  @Override
  public @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    if (!TrustedProjects.isTrusted(project)) return Collections.emptySet();

    VcsProjectLog projectLog = project.getServiceIfCreated(VcsProjectLog.class);
    if (projectLog == null) return Collections.emptySet();
    VcsProjectLogManager logManager = projectLog.getProjectLogManager();
    if (logManager == null) return Collections.emptySet();

    MainVcsLogUi mainUi = projectLog.getMainLogUi();
    Set<String> additionalTabIds = logManager.getTabsManager().getTabs();
    List<? extends MainVcsLogUi> additionalToolWindowUis = getAdditionalLogUis(logManager.getLogUis(VcsLogTabLocation.TOOL_WINDOW), additionalTabIds);
    List<? extends MainVcsLogUi> additionalEditorUis = getAdditionalLogUis(logManager.getLogUis(VcsLogTabLocation.EDITOR), additionalTabIds);
    if (mainUi == null && additionalToolWindowUis.isEmpty() && additionalEditorUis.isEmpty()) return Collections.emptySet();

    Set<MetricEvent> metricEvents = ContainerUtil.newHashSet(UI_INITIALIZED.metric());

    recordApplicationProperties(ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class), metricEvents);
    if (mainUi != null) {
      metricEvents.add(MAIN_UI_INITIALIZED.metric());
      recordUiProperties(mainUi, metricEvents);
    }
    for (MainVcsLogUi ui : ContainerUtil.union(additionalToolWindowUis, additionalEditorUis)) {
      recordUiProperties(ui, metricEvents);
    }

    if (!additionalToolWindowUis.isEmpty()) {
      metricEvents.add(ADDITIONAL_TOOL_WINDOW_TABS.metric(EventFields.Count.with(additionalToolWindowUis.size())));
    }
    if (!additionalEditorUis.isEmpty()) {
      metricEvents.add(ADDITIONAL_EDITOR_TABS.metric(EventFields.Count.with(additionalEditorUis.size())));
    }

    return metricEvents;
  }

  private static void recordApplicationProperties(@NotNull VcsLogApplicationSettings properties, @NotNull Set<MetricEvent> metricEvents) {
    VcsLogApplicationSettings defaultProperties = new VcsLogApplicationSettings();

    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_DIFF_PREVIEW), DIFF_PREVIEW);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(DIFF_PREVIEW_VERTICAL_SPLIT), DIFF_PREVIEW_ON_THE_BOTTOM);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_CHANGES_FROM_PARENTS), PARENT_CHANGES);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(COMPACT_REFERENCES_VIEW), LABELS_COMPACT);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_TAG_NAMES), LABELS_SHOW_TAG_NAMES);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(LABELS_LEFT_ALIGNED), LABELS_ON_THE_LEFT);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(PREFER_COMMIT_DATE), SHOW_COMMIT_DATE);

    VcsLogColumnManager modelIndices = VcsLogColumnManager.getInstance();
    Set<Integer> currentColumns = ContainerUtil.map2Set(getColumnsOrder(properties), it -> modelIndices.getModelIndex(it));
    Set<Integer> defaultColumns = ContainerUtil.map2Set(getColumnsOrder(defaultProperties), it -> modelIndices.getModelIndex(it));
    for (VcsLogDefaultColumn<?> column : getDefaultDynamicColumns()) {
      String columnName = column.getStableName();
      addBoolIfDiffers(metricEvents, currentColumns, defaultColumns, p -> p.contains(modelIndices.getModelIndex(column)),
                       COLUMN, new ArrayList<>(List.of(COLUMN_NAME.with(columnName))));
    }
  }

  private static void recordUiProperties(@NotNull MainVcsLogUi ui, @NotNull Set<MetricEvent> metricEvents) {
    MainVcsLogUiProperties properties = ui.getProperties();
    VcsLogUiProperties defaultProperties = createDefaultPropertiesInstance();

    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_DETAILS), DETAILS);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_ONLY_AFFECTED_CHANGES), ONLY_AFFECTED_CHANGES);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_LONG_EDGES), LONG_EDGES);

    addIfDiffers(metricEvents, properties, defaultProperties, p -> GraphOptionsUtil.getKindName(p.get(GRAPH_OPTIONS)),
                 GRAPH_OPTIONS_TYPE, GRAPH_OPTIONS_TYPE_FIELD);
    if (properties.get(GRAPH_OPTIONS) instanceof PermanentGraph.Options.Base) {
      addIfDiffers(metricEvents, properties, defaultProperties, p -> {
        PermanentGraph.Options options = p.get(GRAPH_OPTIONS);
        if (options instanceof PermanentGraph.Options.Base baseOptions) {
          return baseOptions.getSortType();
        }
        return null;
      }, SORT, SORT_TYPE_FIELD);
    }

    if (ui.getTable().getColorManager().hasMultiplePaths()) {
      addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_ROOT_NAMES), ROOTS);
    }

    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(MainVcsLogUiProperties.TEXT_FILTER_REGEX),
                     TEXT_FILTER_REGEX);
    addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE),
                     TEXT_FILTER_MATCH_CASE);

    for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensionList()) {
      if (factory.showMenuItem()) {
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(VcsLogHighlighterProperty.get(factory.getId())),
                         HIGHLIGHTER, new ArrayList<>(List.of(LOG_HIGHLIGHTER_ID_FIELD.with(getFactoryIdSafe(factory)))));
      }
    }

    for (VcsLogFilterCollection.FilterKey<?> key : VcsLogFilterCollection.STANDARD_KEYS) {
      if (properties.getFilterValues(key.getName()) != null) {
        metricEvents.add(FILTER.metric(EventFields.Enabled.with(true), FILTER_NAME.with(key.getName())));
      }
    }
  }

  private static @Unmodifiable @NotNull List<? extends MainVcsLogUi> getAdditionalLogUis(@NotNull List<? extends VcsLogUi> uis,
                                                                                         @NotNull Set<String> additionalTabIds) {
    return ContainerUtil.filter(ContainerUtil.filterIsInstance(uis, MainVcsLogUi.class),
                                ui -> additionalTabIds.contains(ui.getId()));
  }

  private static @NotNull String getFactoryIdSafe(@NotNull VcsLogHighlighterFactory factory) {
    if (PluginInfoDetectorKt.getPluginInfo(factory.getClass()).isDevelopedByJetBrains()) {
      return UsageDescriptorKeyValidator.ensureProperKey(factory.getId());
    }
    return THIRD_PARTY;
  }

  private static @NotNull <T> Function1<VcsLogUiProperties, T> getter(@NotNull VcsLogUiProperty<? extends T> property) {
    return p -> {
      if (!p.exists(property)) return null;
      return p.get(property);
    };
  }

  private static @NotNull VcsLogUiProperties createDefaultPropertiesInstance() {
    return new VcsLogUiPropertiesImpl<VcsLogUiPropertiesImpl.State>(new VcsLogApplicationSettings()) {
      private final @NotNull State myState = new State();

      @Override
      protected @NotNull State getLogUiState() {
        return myState;
      }

      @Override
      public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
        throw new UnsupportedOperationException();
      }

      @Override
      public @NotNull List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
