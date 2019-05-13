// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;
import static java.util.Arrays.asList;

public class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      VcsLogUiImpl ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();
        VcsLogUiProperties defaultProperties = createDefaultPropertiesInstance();

        Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
        addBooleanUsage(properties, defaultProperties, usages, "details", CommonUiProperties.SHOW_DETAILS);
        addBooleanUsage(properties, defaultProperties, usages, "diffPreview", CommonUiProperties.SHOW_DIFF_PREVIEW);
        addBooleanUsage(properties, defaultProperties, usages, "long.edges", SHOW_LONG_EDGES);

        addEnumUsage(properties, defaultProperties, usages, "sort", BEK_SORT_TYPE);

        if (ui.getColorManager().isMultipleRoots()) {
          addBooleanUsage(properties, defaultProperties, usages, "roots", CommonUiProperties.SHOW_ROOT_NAMES);
        }

        addBooleanUsage(properties, defaultProperties, usages, "labels.compact", COMPACT_REFERENCES_VIEW);
        addBooleanUsage(properties, defaultProperties, usages, "labels.showTagNames", SHOW_TAG_NAMES);

        addBooleanUsage(properties, defaultProperties, usages, "textFilter.regex", TEXT_FILTER_REGEX);
        addBooleanUsage(properties, defaultProperties, usages, "textFilter.matchCase", TEXT_FILTER_MATCH_CASE);

        for (VcsLogHighlighterFactory factory : LOG_HIGHLIGHTER_FACTORY_EP.getExtensions(project)) {
          if (factory.showMenuItem()) {
            addBooleanUsage(properties, defaultProperties, usages,
                            "highlighter." + UsageDescriptorKeyValidator.ensureProperKey(factory.getId()),
                            VcsLogHighlighterProperty.get(factory.getId()));
          }
        }

        List<String> tabs = projectLog.getTabsManager().getTabs();
        usages.add(StatisticsUtilKt.getCountingUsage("additionalTabs.count", tabs.size(), asList(0, 1, 2, 3, 4, 8)));

        return usages;
      }
    }
    return Collections.emptySet();
  }

  private static void addBooleanUsage(@NotNull VcsLogUiProperties properties,
                                      @NotNull VcsLogUiProperties defaultProperties,
                                      @NotNull Set<? super UsageDescriptor> usages,
                                      @NotNull String usageName,
                                      @NotNull VcsLogUiProperty<Boolean> property) {
    addUsageIfNotDefault(properties, defaultProperties, usages, property, value -> StatisticsUtilKt.getBooleanUsage(usageName, value));
  }

  private static void addEnumUsage(@NotNull VcsLogUiProperties properties,
                                   @NotNull VcsLogUiProperties defaultProperties,
                                   @NotNull Set<? super UsageDescriptor> usages,
                                   @NotNull String usageName,
                                   @NotNull VcsLogUiProperty<? extends Enum> property) {
    addUsageIfNotDefault(properties, defaultProperties, usages, property, value -> StatisticsUtilKt.getEnumUsage(usageName, value));
  }

  private static <T> void addUsageIfNotDefault(@NotNull VcsLogUiProperties properties,
                                               @NotNull VcsLogUiProperties defaultProperties,
                                               @NotNull Set<? super UsageDescriptor> usages,
                                               @NotNull VcsLogUiProperty<T> property,
                                               @NotNull Function<? super T, ? extends UsageDescriptor> createUsage) {
    if (!properties.exists(property)) return;

    T value = properties.get(property);
    if (!Objects.equals(defaultProperties.get(property), value)) {
      usages.add(createUsage.fun(value));
    }
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
    return "statistics.vcs.log.ui";
  }
}
