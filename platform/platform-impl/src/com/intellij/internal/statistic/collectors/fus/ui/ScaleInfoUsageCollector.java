// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.jdkEx.JdkEx;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class ScaleInfoUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    return getDescriptors();
  }

  @NotNull
  public static Set<MetricEvent> getDescriptors() {
    float scale = JBUIScale.sysScale();

    int scaleBase = (int)Math.floor(scale);
    float scaleFract = scale - scaleBase;

    if (scaleFract == 0.0f) scaleFract = 0.0f; // count integer scale on a precise match only
    else if (scaleFract < 0.375f) scaleFract = 0.25f;
    else if (scaleFract < 0.625f) scaleFract = 0.5f;
    else scaleFract = 0.75f;

    scale = scaleBase + scaleFract;

    String prefix = "";
    if (!GraphicsEnvironment.isHeadless()) {
      DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
      if (JdkEx.getDisplayModeEx().isDefault(dm)) prefix += "ScaledMode_";
    }

    final String key = prefix.length() == 0 ? String.valueOf(scale) : prefix + scale;
    return Collections.singleton(new MetricEvent(key));
  }

  @NotNull
  @Override
  public String getGroupId() { return "ui.screen.scale"; }
}
