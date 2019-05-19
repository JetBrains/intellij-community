// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.1")
public abstract class AbstractProjectsUsagesCollector extends UsagesCollector {

  @NotNull
  public abstract Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException;

  @Override
  @NotNull
  public final Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    return Collections.emptySet();
  }
}
