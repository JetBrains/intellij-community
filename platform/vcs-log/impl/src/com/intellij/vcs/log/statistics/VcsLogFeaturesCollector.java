// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.highlighters.CurrentBranchHighlighter;
import com.intellij.vcs.log.ui.highlighters.MergeCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.MyCommitsHighlighter;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.ui.table.column.VcsLogDefaultColumn;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addBoolIfDiffers;
import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addIfDiffers;
import static com.intellij.vcs.log.impl.CommonUiProperties.*;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.getColumnsOrder;
import static com.intellij.vcs.log.ui.table.column.VcsLogDefaultColumnKt.getDefaultDynamicColumns;

@NonNls
public class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("vcs.log.ui", 3);
  private static final EventId UI_INITIALIZED = GROUP.registerEvent("uiInitialized");
  private static final VarargEventId DETAILS = GROUP.registerVarargEvent("details", EventFields.Enabled);
  private static final VarargEventId DIFF_PREVIEW = GROUP.registerVarargEvent("diffPreview", EventFields.Enabled);
  private static final VarargEventId DIFF_PREVIEW_ON_THE_BOTTOM = GROUP.registerVarargEvent("diffPreviewOnTheBottom", EventFields.Enabled);
  private static final VarargEventId PARENT_CHANGES = GROUP.registerVarargEvent("parentChanges", EventFields.Enabled);
  private static final VarargEventId ONLY_AFFECTED_CHANGES = GROUP.registerVarargEvent("onlyAffectedChanges", EventFields.Enabled);
  private static final VarargEventId LONG_EDGES = GROUP.registerVarargEvent("long.edges", EventFields.Enabled);
  private static final EnumEventField<PermanentGraph.SortType> SORT_TYPE_FIELD =
    EventFields.Enum("value", PermanentGraph.SortType.class);
  private static final VarargEventId SORT = GROUP.registerVarargEvent("sort", EventFields.Enabled, SORT_TYPE_FIELD);
  private static final VarargEventId ROOTS = GROUP.registerVarargEvent("roots", EventFields.Enabled);
  private static final VarargEventId LABELS_COMPACT = GROUP.registerVarargEvent("labels.compact", EventFields.Enabled);
  private static final VarargEventId LABELS_SHOW_TAG_NAMES = GROUP.registerVarargEvent("labels.showTagNames", EventFields.Enabled);
  private static final VarargEventId LABELS_ON_THE_LEFT = GROUP.registerVarargEvent("labels.onTheLeft", EventFields.Enabled);
  private static final VarargEventId TEXT_FILTER_REGEX = GROUP.registerVarargEvent("textFilter.regex", EventFields.Enabled);
  private static final VarargEventId TEXT_FILTER_MATCH_CASE = GROUP.registerVarargEvent("textFilter.matchCase", EventFields.Enabled);
  public static final String THIRD_PARTY = "THIRD_PARTY";
  private static final StringEventField LOG_HIGHLIGHTER_ID_FIELD =
    EventFields.String("id", List.of(MyCommitsHighlighter.Factory.ID,
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
  private static final VarargEventId ADDITIONAL_TABS = GROUP.registerVarargEvent("additionalTabs", EventFields.Count);

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      MainVcsLogUi ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();
        VcsLogUiProperties defaultProperties = createDefaultPropertiesInstance();

        Set<MetricEvent> metricEvents = ContainerUtil.newHashSet(UI_INITIALIZED.metric());

        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_DETAILS), DETAILS);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_DIFF_PREVIEW), DIFF_PREVIEW);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(DIFF_PREVIEW_VERTICAL_SPLIT), DIFF_PREVIEW_ON_THE_BOTTOM);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_CHANGES_FROM_PARENTS), PARENT_CHANGES);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_ONLY_AFFECTED_CHANGES), ONLY_AFFECTED_CHANGES);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_LONG_EDGES), LONG_EDGES);

        addIfDiffers(metricEvents, properties, defaultProperties, getter(BEK_SORT_TYPE), SORT, SORT_TYPE_FIELD);

        if (ui.getColorManager().hasMultiplePaths()) {
          addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_ROOT_NAMES), ROOTS);
        }

        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(COMPACT_REFERENCES_VIEW), LABELS_COMPACT);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_TAG_NAMES), LABELS_SHOW_TAG_NAMES);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(LABELS_LEFT_ALIGNED), LABELS_ON_THE_LEFT);
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(MainVcsLogUiProperties.TEXT_FILTER_REGEX), TEXT_FILTER_REGEX);
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

        VcsLogColumnManager modelIndices = VcsLogColumnManager.getInstance();
        Set<Integer> currentColumns = ContainerUtil.map2Set(getColumnsOrder(properties), it -> modelIndices.getModelIndex(it));
        Set<Integer> defaultColumns = ContainerUtil.map2Set(getColumnsOrder(defaultProperties), it -> modelIndices.getModelIndex(it));
        for (VcsLogDefaultColumn<?> column : getDefaultDynamicColumns()) {
          String columnName = column.getStableName();
          addBoolIfDiffers(metricEvents, currentColumns, defaultColumns, p -> p.contains(modelIndices.getModelIndex(column)),
                           COLUMN, new ArrayList<>(List.of(COLUMN_NAME.with(columnName))));
        }

        Collection<String> tabs = projectLog.getTabsManager().getTabs();
        metricEvents.add(ADDITIONAL_TABS.metric(EventFields.Count.with(tabs.size())));

        return metricEvents;
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  private static String getFactoryIdSafe(@NotNull VcsLogHighlighterFactory factory) {
    if (PluginInfoDetectorKt.getPluginInfo(factory.getClass()).isDevelopedByJetBrains()) {
      return UsageDescriptorKeyValidator.ensureProperKey(factory.getId());
    }
    return THIRD_PARTY;
  }

  @NotNull
  private static <T> Function1<VcsLogUiProperties, T> getter(@NotNull VcsLogUiProperty<? extends T> property) {
    return p -> {
      if (!p.exists(property)) return null;
      return p.get(property);
    };
  }

  @NotNull
  private static VcsLogUiProperties createDefaultPropertiesInstance() {
    return new VcsLogUiPropertiesImpl<>(new VcsLogApplicationSettings()) {
      @NotNull private final State myState = new State();

      @NotNull
      @Override
      public State getState() {
        return myState;
      }

      @Override
      public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
        throw new UnsupportedOperationException();
      }

      @NotNull
      @Override
      public List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void loadState(@NotNull State state) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
