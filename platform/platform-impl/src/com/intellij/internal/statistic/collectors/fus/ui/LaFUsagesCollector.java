// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;

public class LaFUsagesCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    return laf != null ? Collections.singleton(new MetricEvent(ensureProperKey(laf.getName())))
                       : Collections.emptySet();
  }

  @NotNull
  @Override
  public String getGroupId() { return "ui.look.and.feel"; }
}
