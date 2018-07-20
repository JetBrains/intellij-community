/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

@Deprecated // to be removed in 2018.2
public abstract class AbstractProjectsUsagesCollector extends UsagesCollector {

  @NotNull
  public abstract Set<UsageDescriptor> getProjectUsages(@NotNull Project project) throws CollectUsagesException;

  @Override
  @NotNull
  public final Set<UsageDescriptor> getUsages() throws CollectUsagesException {
    return Collections.emptySet();
  }
}
