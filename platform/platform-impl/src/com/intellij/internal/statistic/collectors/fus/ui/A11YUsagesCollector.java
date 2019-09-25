// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class A11YUsagesCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    String activity = ScreenReader.isActive() ? "ENABLED" : "DISABLED";
    return Collections.singleton(new MetricEvent("screen.reader." + activity));
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "ui.accessibility.screen.reader";
  }
}

