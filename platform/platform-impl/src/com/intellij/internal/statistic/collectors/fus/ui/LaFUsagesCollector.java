// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;

public class LaFUsagesCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    String key = SystemInfo.OS_NAME + ".";
    if (!StringUtil.isEmptyOrSpaces(SystemInfo.SUN_DESKTOP)) {
      key=key.replaceAll(" ", "_");
      key += SystemInfo.SUN_DESKTOP + ".";
    }
    return laf != null ? Collections.singleton(new UsageDescriptor(ensureProperKey(key + laf.getName()), 1))
                       : Collections.emptySet();
  }

  @NotNull
  @Override
  public String getGroupId() { return "statistics.ui.look.and.feel"; }
}
