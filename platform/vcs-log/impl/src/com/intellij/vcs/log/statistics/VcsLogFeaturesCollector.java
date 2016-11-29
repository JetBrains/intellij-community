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

import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.StatisticsUtilKt;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.ui.VcsLogHighlighterFactory;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.vcs.log.ui.VcsLogUiImpl.LOG_HIGHLIGHTER_FACTORY_EP;

public class VcsLogFeaturesCollector extends AbstractApplicationUsagesCollector {
  public static final GroupDescriptor ID = GroupDescriptor.create("VCS Log Ui Settings");

  @NotNull
  @Override
  public Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException {
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    if (projectLog != null) {
      VcsLogUiImpl ui = projectLog.getMainLogUi();
      if (ui != null) {
        Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.details", ui.isShowDetails()));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.long.edges", ui.areLongEdgesVisible()));

        usages.add(StatisticsUtilKt.getBooleanUsage("ui.sort.linear.bek", ui.getBekType().equals(PermanentGraph.SortType.LinearBek)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.sort.bek", ui.getBekType().equals(PermanentGraph.SortType.Bek)));
        usages.add(StatisticsUtilKt.getBooleanUsage("ui.sort.normal", ui.getBekType().equals(PermanentGraph.SortType.Normal)));

        if (ui.isMultipleRoots()) {
          usages.add(StatisticsUtilKt.getBooleanUsage("ui.roots", ui.isShowRootNames()));
        }

        for (VcsLogHighlighterFactory factory : Extensions.getExtensions(LOG_HIGHLIGHTER_FACTORY_EP, project)) {
          if (factory.showMenuItem()) {
            usages.add(StatisticsUtilKt.getBooleanUsage("ui.highlighter." + ConvertUsagesUtil
              .ensureProperKey(factory.getId()), ui.isHighlighterEnabled(factory.getId())));
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
