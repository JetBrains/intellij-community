// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.util.ui.JBUI;
import org.jdesktop.swingx.util.OS;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class ScaleInfoUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getDescriptors();
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors() {
    float scale = JBUI.sysScale();

    int scaleBase = (int)Math.floor(scale);
    float scaleFract = scale - scaleBase;

    if (scaleFract == 0.0f) scaleFract = 0.0f; // count integer scale on a precise match only
    else if (scaleFract < 0.375f) scaleFract = 0.25f;
    else if (scaleFract < 0.625f) scaleFract = 0.5f;
    else scaleFract = 0.75f;

    scale = scaleBase + scaleFract;

    String os = OS.isWindows() ? "Windows" : OS.isLinux() ? "Linux" : OS.isMacOSX() ? "Mac" : "UnknownOS";
    return Collections.singleton(new UsageDescriptor(os + "_" + scale, 1));
  }

  @NotNull
  @Override
  public String getGroupId() { return "statistics.ui.screen.scale"; }
}
