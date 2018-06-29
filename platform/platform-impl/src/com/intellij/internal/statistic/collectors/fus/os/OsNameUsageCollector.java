// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os;

import com.google.common.collect.ImmutableSet;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

import static com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey;

public class OsNameUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    String osName = SystemInfo.isLinux ? "Linux" : SystemInfo.isMac ? "Mac.OS.X" : SystemInfo.isWindows ? "Windows" : SystemInfo.OS_NAME;
    return ImmutableSet.of(new UsageDescriptor(ensureProperKey(osName), 1));
  }

  @NotNull
  @Override
  public String getGroupId() { return "statistics.os.name"; }
}
