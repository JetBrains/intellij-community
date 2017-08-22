/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.statistic.customUsageCollectors.ui;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.ui.JBUI;
import org.jdesktop.swingx.util.OS;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author tav
 */
public class ScaleInfoUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    float scale = JBUI.sysScale();

    int scaleBase = (int)Math.floor(scale);
    float scaleFract = scale - scaleBase;

    if (scaleFract == 0.0f) scaleFract = 0.0f; // count integer scale on a precise match only
    else if (scaleFract < 0.375f) scaleFract = 0.25f;
    else if (scaleFract < 0.625f) scaleFract = 0.5f;
    else scaleFract = 0.75f;

    scale = scaleBase + scaleFract;

    String os = OS.isWindows() ? "Windows" : OS.isLinux() ? "Linux" : OS.isMacOSX() ? "Mac" : "Unknown OS";
    return Collections.singleton(new UsageDescriptor(os + " " + scale, 1));
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.ui.screen.scale");
  }
}
