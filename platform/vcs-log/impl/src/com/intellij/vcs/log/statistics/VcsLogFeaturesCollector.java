/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.statistics;

import com.intellij.internal.statistic.AbstractProjectsUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
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

public class VcsLogFeaturesCollector extends AbstractProjectsUsagesCollector {
  public static final GroupDescriptor ID = GroupDescriptor.create("VCS Log Ui Settings");

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      VcsLogUiImpl ui = projectLog.getMainLogUi();
      if (ui != null) {
        MainVcsLogUiProperties properties = ui.getProperties();

        Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.details", properties.get(CommonUiProperties.SHOW_DETAILS)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.long.edges", properties.get(SHOW_LONG_EDGES)));

        PermanentGraph.SortType sortType = properties.get(BEK_SORT_TYPE);
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.sort.linear.bek", sortType.equals(PermanentGraph.SortType.LinearBek)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.sort.bek", sortType.equals(PermanentGraph.SortType.Bek)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.sort.normal", sortType.equals(PermanentGraph.SortType.Normal)));

        if (ui.isMultipleRoots()) {
          usages.add(StatisticsUtilKt.getBooleanUsage("ui.roots", properties.get(SHOW_ROOT_NAMES)));
        }

        usages.add(StatisticsUtilKt.getBooleanUsage("ui.labels.compact", properties.get(COMPACT_REFERENCES_VIEW)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.labels.showTagNames", properties.get(SHOW_TAG_NAMES)));

        usages.add(StatisticsUtilKt.getBooleanUsage("ui.textFilter.regex", properties.get(TEXT_FILTER_REGEX)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.textFilter.matchCase", properties.get(TEXT_FILTER_MATCH_CASE)));

        for (VcsLogHighlighterFactory factory : Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, project)) {
          if (factory.showMenuItem()) {
            VcsLogHighlighterProperty property = VcsLogHighlighterProperty.get(factory.getId());
            usages.add(StatisticsUtilKt.getBooleanUsage("ui.highlighter." + ConvertUsagesUtil.ensureProperKey(factory.getId()),
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
  public GroupDescriptor getGroupId() {
    return ID;
  }
}
