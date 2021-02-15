// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.ui.table.column.VcsLogDefaultColumn;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.internal.statistic.beans.MetricEventFactoryKt.newBooleanMetric;
import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addBoolIfDiffers;
import static com.intellij.internal.statistic.beans.MetricEventUtilKt.addIfDiffers;
import static com.intellij.vcs.log.impl.CommonUiProperties.*;
import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;
import static com.intellij.vcs.log.ui.table.column.VcsLogColumnUtilKt.getColumnsOrder;
import static com.intellij.vcs.log.ui.table.column.VcsLogDefaultColumnKt.getDefaultDynamicColumns;

@NonNls
public class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<MetricEvent> getMetrics(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      MainVcsLogUi ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();
        VcsLogUiProperties defaultProperties = createDefaultPropertiesInstance();

        Set<MetricEvent> metricEvents = ContainerUtil.newHashSet(new MetricEvent("uiInitialized"));

        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_DETAILS), "details");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_DIFF_PREVIEW), "diffPreview");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(DIFF_PREVIEW_VERTICAL_SPLIT), "diffPreviewOnTheBottom");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_CHANGES_FROM_PARENTS), "parentChanges");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_ONLY_AFFECTED_CHANGES), "onlyAffectedChanges");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_LONG_EDGES), "long.edges");

        addIfDiffers(metricEvents, properties, defaultProperties, getter(BEK_SORT_TYPE), "sort");

        if (ui.getColorManager().hasMultiplePaths()) {
          addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_ROOT_NAMES), "roots");
        }

        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(COMPACT_REFERENCES_VIEW), "labels.compact");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(SHOW_TAG_NAMES), "labels.showTagNames");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(LABELS_LEFT_ALIGNED), "labels.onTheLeft");

        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(TEXT_FILTER_REGEX), "textFilter.regex");
        addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(TEXT_FILTER_MATCH_CASE), "textFilter.matchCase");

        for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensionList()) {
          if (factory.showMenuItem()) {
            addBoolIfDiffers(metricEvents, properties, defaultProperties, getter(VcsLogHighlighterProperty.get(factory.getId())),
                             "highlighter", new FeatureUsageData().addData("id", getFactoryIdSafe(factory)));
          }
        }

        for (VcsLogFilterCollection.FilterKey<?> key : VcsLogFilterCollection.STANDARD_KEYS) {
          if (properties.getFilterValues(key.getName()) != null) {
            metricEvents.add(newBooleanMetric("filter", true, new FeatureUsageData().addData("name", key.getName())));
          }
        }

        VcsLogColumnManager modelIndices = VcsLogColumnManager.getInstance();
        Set<Integer> currentColumns = ContainerUtil.map2Set(getColumnsOrder(properties), it -> modelIndices.getModelIndex(it));
        Set<Integer> defaultColumns = ContainerUtil.map2Set(getColumnsOrder(defaultProperties), it -> modelIndices.getModelIndex(it));
        for (VcsLogDefaultColumn<?> column : getDefaultDynamicColumns()) {
          String columnName = column.getStableName();
          addBoolIfDiffers(metricEvents, currentColumns, defaultColumns, p -> p.contains(modelIndices.getModelIndex(column)),
                           "column", new FeatureUsageData().addData("name", columnName));
        }

        Collection<String> tabs = projectLog.getTabsManager().getTabs();
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

  @NotNull
  @Override
  public String getGroupId() {
    return "vcs.log.ui";
  }

  @Override
  public int getVersion() {
    return 2;
  }
}
