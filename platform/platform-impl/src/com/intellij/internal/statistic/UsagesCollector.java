// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Deprecated // to be removed in 2019.1
@ApiStatus.ScheduledForRemoval(inVersion = "2019.1")
public abstract class UsagesCollector {

  public static final ExtensionPointName<UsagesCollector> EP_NAME = ExtensionPointName.create("com.intellij.statistics.usagesCollector");

  @NotNull
  public abstract Set<UsageDescriptor> getUsages() throws CollectUsagesException;

  @NotNull
  public abstract GroupDescriptor getGroupId();

}
