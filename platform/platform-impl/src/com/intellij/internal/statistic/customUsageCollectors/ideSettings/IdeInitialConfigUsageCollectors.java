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
package com.intellij.internal.statistic.customUsageCollectors.ideSettings;

import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.customUsageCollectors.ideSettings.IdeInitialConfigButtonUsages.*;

public final class IdeInitialConfigUsageCollectors {
  public static class ConfigImport extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() throws CollectUsagesException {
      return Collections.singleton(StatisticsUtilKt.getEnumUsage("import", getConfigImport()));
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Import settings");
    }
  }

  public static class SkipSteps extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() throws CollectUsagesException {
      return Collections.singleton(new UsageDescriptor(getSkipRemainingPressedScreen(), 1));
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard:Skip remaining pressed");
    }
  }

  public static class DisabledPlugins extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() throws CollectUsagesException {
      return getPredefinedDisabledPlugins().stream()
        .map(pluginDescriptor -> new UsageDescriptor(pluginDescriptor, 1))
        .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard:Disabled plugins");
    }
  }

  public static class DownloadedPlugins extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() throws CollectUsagesException {
      return getDownloadedPlugins().stream()
        .map(pluginId -> new UsageDescriptor(pluginId, 1))
        .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard:Downloaded plugins");
    }
  }

  public static class SelectedKeymap extends Base {

    @Override
    protected Set<UsageDescriptor> doGetUsages() throws CollectUsagesException {
      final String keymapName = WelcomeWizardUtil.getWizardMacKeymap();
      if (StringUtil.isEmpty(keymapName)) {
        return Collections.emptySet();
      }
      else {
        return Collections.singleton(new UsageDescriptor(keymapName, 1));
      }
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard:Selected keymap");
    }
  }

  public static class SelectedLAF extends Base {

    @Override
    protected Set<UsageDescriptor> doGetUsages() throws CollectUsagesException {
      final String laf = WelcomeWizardUtil.getWizardLAF();
      if (StringUtil.isEmpty(laf)) {
        return Collections.emptySet();
      }
      else {
        return Collections.singleton(new UsageDescriptor(laf, 1));
      }
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard:Selected LAF");
    }
  }

  private abstract static class Base extends UsagesCollector {
    protected abstract Set<UsageDescriptor> doGetUsages() throws CollectUsagesException;

    private static boolean shouldCount() {
      return getConfigImport() != IdeInitialConfigButtonUsages.ConfigImport.NO_INIT;
    }

    @NotNull
    @Override
    public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
      if (!shouldCount()) {
        return Collections.emptySet();
      }
      return doGetUsages();
    }
  }
}
