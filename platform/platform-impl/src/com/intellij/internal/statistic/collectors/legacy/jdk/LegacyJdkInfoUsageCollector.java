// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.jdk;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@Deprecated // to be removed in 2018.2
class LegacyJdkInfoUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    final String vendor = System.getProperty("java.vendor", "Unknown");
    final String version = "1." + JavaVersion.current().feature;
    return Collections.singleton(new UsageDescriptor(vendor + " " + version, 1));
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.jdk");
  }
}