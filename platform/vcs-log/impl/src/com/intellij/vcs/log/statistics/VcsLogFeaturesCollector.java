// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.beans.MetricEventUtilKt;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.GraphTableModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;

public class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      VcsLogUiImpl ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();
        VcsLogUiProperties defaultProperties = createDefaultPropertiesInstance();

        Set<MetricEvent> metricEvents = ContainerUtil.newHashSet(new MetricEvent("uiInitialized"));

        addBooleanUsage(properties, defaultProperties, metricEvents, "details", CommonUiProperties.SHOW_DETAILS);
        addBooleanUsage(properties, defaultProperties, metricEvents, "diffPreview", CommonUiProperties.SHOW_DIFF_PREVIEW);
        addBooleanUsage(properties, defaultProperties, metricEvents, "parentChanges", SHOW_CHANGES_FROM_PARENTS);
        addBooleanUsage(properties, defaultProperties, metricEvents, "onlyAffectedChanges", SHOW_ONLY_AFFECTED_CHANGES);
        addBooleanUsage(properties, defaultProperties, metricEvents, "long.edges", SHOW_LONG_EDGES);

        addEnumUsage(properties, defaultProperties, metricEvents, "sort", BEK_SORT_TYPE);

        if (ui.getColorManager().hasMultiplePaths()) {
          addBooleanUsage(properties, defaultProperties, metricEvents, "roots", CommonUiProperties.SHOW_ROOT_NAMES);
        }

        addBooleanUsage(properties, defaultProperties, metricEvents, "labels.compact", COMPACT_REFERENCES_VIEW);
        addBooleanUsage(properties, defaultProperties, metricEvents, "labels.showTagNames", SHOW_TAG_NAMES);

        addBooleanUsage(properties, defaultProperties, metricEvents, "textFilter.regex", TEXT_FILTER_REGEX);
        addBooleanUsage(properties, defaultProperties, metricEvents, "textFilter.matchCase", TEXT_FILTER_MATCH_CASE);

        for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensions(project)) {
          if (factory.showMenuItem()) {
            addBooleanUsage(properties, defaultProperties, metricEvents, "highlighter." + getFactoryIdSafe(factory),
                            VcsLogHighlighterProperty.get(factory.getId()));
          }
        }

        for (VcsLogFilterCollection.FilterKey<?> key : VcsLogFilterCollection.STANDARD_KEYS) {
          if (properties.getFilterValues(key.getName()) != null) {
            metricEvents.add(MetricEventFactoryKt.newBooleanMetric(key.getName() + "Filter", true));
          }
        }

        Set<Integer> currentColumns = new HashSet<>(properties.get(CommonUiProperties.COLUMN_ORDER));
        Set<Integer> defaultColumns = new HashSet<>(defaultProperties.get(CommonUiProperties.COLUMN_ORDER));
        for (int column : GraphTableModel.DYNAMIC_COLUMNS) {
          MetricEventUtilKt.addBoolIfDiffers(metricEvents, currentColumns, defaultColumns, p -> p.contains(column),
                                             StringUtil.toLowerCase(GraphTableModel.COLUMN_NAMES[column]) + "Column");
        }

        List<String> tabs = projectLog.getTabsManager().getTabs();
        metricEvents.add(MetricEventFactoryKt.newCounterMetric("additionalTabs", tabs.size()));

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
    return "THIRD_PARTY";
  }

  private static void addBooleanUsage(@NotNull VcsLogUiProperties properties,
                                      @NotNull VcsLogUiProperties defaultProperties,
                                      @NotNull Set<? super MetricEvent> metricEvents,
                                      @NotNull String metricsName,
                                      @NotNull VcsLogUiProperty<Boolean> property) {
    if (!properties.exists(property)) return;
    MetricEventUtilKt.addBoolIfDiffers(metricEvents, properties, defaultProperties, p -> p.get(property), metricsName);
  }

  private static void addEnumUsage(@NotNull VcsLogUiProperties properties,
                                   @NotNull VcsLogUiProperties defaultProperties,
                                   @NotNull Set<? super MetricEvent> metricEvents,
                                   @NotNull String metricsName,
                                   @NotNull VcsLogUiProperty<? extends Enum> property) {
    if (!properties.exists(property)) return;
    MetricEventUtilKt.addIfDiffers(metricEvents, properties, defaultProperties, p -> p.get(property), metricsName);
  }

  @NotNull
  private static VcsLogUiProperties createDefaultPropertiesInstance() {
    return new VcsLogUiPropertiesImpl(new VcsLogApplicationSettings()) {
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
      public void loadState(@NotNull Object state) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "vcs.log.ui";
  }
}
