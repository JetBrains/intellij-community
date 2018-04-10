// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.legacy.os;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.collectors.fus.os.OsVersionUsageCollector;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
@Deprecated
  // to be removed in 2018.2
class LegacyOsVersionUsageCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return OsVersionUsageCollector.getDescriptors();
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("user.os.version");
  }
}