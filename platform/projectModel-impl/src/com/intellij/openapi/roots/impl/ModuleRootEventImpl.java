/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeIndexingInfo;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * This is an internal class, use {@link ProjectRootManagerEx#makeRootsChange(Runnable, boolean, boolean)} to fire {@code rootsChanged} event.
 */
@ApiStatus.Internal
public class ModuleRootEventImpl extends ModuleRootEvent {
  private final boolean myFiletypes;
  private final List<? extends RootsChangeIndexingInfo> myInfos;

  public ModuleRootEventImpl(@NotNull Project project, boolean filetypes) {
    this(project, filetypes, Collections.singletonList(RootsChangeIndexingInfo.TOTAL_REINDEX));
  }

  public ModuleRootEventImpl(@NotNull Project project, boolean filetypes, @NotNull List<? extends RootsChangeIndexingInfo> indexingInfos) {
    super(project);
    myFiletypes = filetypes;
    myInfos = indexingInfos;
  }

  @Override
  public boolean isCausedByFileTypesChange() {
    return myFiletypes;
  }


  /**
   * Always `Collections.singletonList(RootsChangeIndexingInfo.TOTAL_REINDEX)` for beforeRootsChangedEvent;
   * provided meaningfully only for rootsChangedEvent.
   * Full reindex is detected by having {@link RootsChangeIndexingInfo#TOTAL_REINDEX} in list
   */
  @NotNull
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public List<? extends RootsChangeIndexingInfo> getInfos() {
    return myInfos;
  }
}
