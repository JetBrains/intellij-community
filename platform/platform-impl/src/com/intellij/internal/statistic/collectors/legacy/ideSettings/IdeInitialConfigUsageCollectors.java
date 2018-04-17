// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.ideSettings;

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

import static com.intellij.internal.statistic.collectors.legacy.ideSettings.IdeInitialConfigButtonUsages.*;

public final class IdeInitialConfigUsageCollectors {
  public static class ConfigImport extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() {
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
    public Set<UsageDescriptor> doGetUsages() {
      return Collections.singleton(new UsageDescriptor(getSkipRemainingPressedScreen(), 1));
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard_Skip remaining pressed");
    }
  }

  public static class DisabledPlugins extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() {
      return getPredefinedDisabledPlugins().stream()
        .map(pluginDescriptor -> new UsageDescriptor(pluginDescriptor, 1))
        .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard_Disabled plugins");
    }
  }

  public static class DownloadedPlugins extends Base {

    @NotNull
    @Override
    public Set<UsageDescriptor> doGetUsages() {
      return getDownloadedPlugins().stream()
        .map(pluginId -> new UsageDescriptor(pluginId, 1))
        .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public GroupDescriptor getGroupId() {
      return GroupDescriptor.create("Wizard_Downloaded plugins");
    }
  }

  public static class SelectedKeymap extends Base {

    @Override
    protected Set<UsageDescriptor> doGetUsages() {
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
      return GroupDescriptor.create("Wizard_Selected keymap");
    }
  }

  public static class SelectedLAF extends Base {

    @Override
    protected Set<UsageDescriptor> doGetUsages() {
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
      return GroupDescriptor.create("Wizard_Selected LAF");
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
