// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class LinuxWindowManagerUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    String wmName = System.getenv("XDG_CURRENT_DESKTOP");

    if (SystemInfo.isLinux && wmName != null) {
      return Collections.singleton(new UsageDescriptor(wmName));
    }

    return Collections.emptySet();
  }

  @NotNull
  @Override
  public String getGroupId() { return "statistics.os.linux.wm"; }

  @Nullable
  @Override
  public FUSUsageContext getContext() {
    return FUSUsageContext.OS_CONTEXT;
  }
}
