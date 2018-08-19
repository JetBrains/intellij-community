// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.highlighters.VcsLogHighlighterFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.vcs.log.impl.MainVcsLogUiProperties.*;
import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;

public class VcsLogFeaturesCollector extends ProjectUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      VcsLogUiImpl ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();

        Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
        usages.add(StatisticsUtilKt.getBooleanUsage("details", properties.get(CommonUiProperties.SHOW_DETAILS)));
        usages.add(StatisticsUtilKt.getBooleanUsage("long.edges", properties.get(SHOW_LONG_EDGES)));

        PermanentGraph.SortType sortType = properties.get(BEK_SORT_TYPE);
        usages.add(StatisticsUtilKt.getBooleanUsage("sort.linear.bek", sortType.equals(PermanentGraph.SortType.LinearBek)));
        usages.add(StatisticsUtilKt.getBooleanUsage("sort.bek", sortType.equals(PermanentGraph.SortType.Bek)));
        usages.add(StatisticsUtilKt.getBooleanUsage("sort.normal", sortType.equals(PermanentGraph.SortType.Normal)));

        if (ui.getColorManager().isMultipleRoots()) {
          usages.add(StatisticsUtilKt.getBooleanUsage("roots", properties.get(CommonUiProperties.SHOW_ROOT_NAMES)));
        }

        usages.add(StatisticsUtilKt.getBooleanUsage("labels.compact", properties.get(COMPACT_REFERENCES_VIEW)));
        usages.add(StatisticsUtilKt.getBooleanUsage("labels.showTagNames", properties.get(SHOW_TAG_NAMES)));

        usages.add(StatisticsUtilKt.getBooleanUsage("textFilter.regex", properties.get(TEXT_FILTER_REGEX)));
        usages.add(StatisticsUtilKt.getBooleanUsage("textFilter.matchCase", properties.get(TEXT_FILTER_MATCH_CASE)));

        for (VcsLogHighlighterFactory factory: Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, project)) {
          if (factory.showMenuItem()) {
            VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(factory.getId());
            usages.add(StatisticsUtilKt.getBooleanUsage("highlighter." + UsageDescriptorKeyValidator.ensureProperKey(factory.getId()),
                                                        properties.exists(property) && properties.get(property)));
          }
        }

        return usages;
      }
    }
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.vcs.log.ui";
  }
}
