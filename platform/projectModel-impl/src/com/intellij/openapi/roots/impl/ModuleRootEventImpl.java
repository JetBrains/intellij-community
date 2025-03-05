// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * This is an internal class, use {@link ProjectRootManagerEx#makeRootsChange(Runnable, RootsChangeRescanningInfo)} to fire {@code rootsChanged} event.
 */
@ApiStatus.Internal
public class ModuleRootEventImpl extends ModuleRootEvent {
  private final boolean myFiletypes;
  private final List<? extends RootsChangeRescanningInfo> myInfos;
  private final boolean myInFromWorkspaceModelOnly;

  public ModuleRootEventImpl(@NotNull Project project, boolean filetypes) {
    this(project, filetypes, Collections.singletonList(RootsChangeRescanningInfo.TOTAL_RESCAN), false);
  }

  public ModuleRootEventImpl(@NotNull Project project,
                             boolean filetypes,
                             @NotNull List<? extends RootsChangeRescanningInfo> indexingInfos,
                             boolean isFromWorkspaceModelOnly) {
    super(project);
    myFiletypes = filetypes;
    myInfos = indexingInfos;
    myInFromWorkspaceModelOnly = isFromWorkspaceModelOnly;
  }

  @Override
  public boolean isCausedByFileTypesChange() {
    return myFiletypes;
  }

  @Override
  public boolean isCausedByWorkspaceModelChangesOnly() {
    return myInFromWorkspaceModelOnly;
  }


  /**
   * Always `Collections.singletonList(RootsChangeRescanningInfo.TOTAL_REINDEX)` for beforeRootsChangedEvent;
   * provided meaningfully only for rootsChangedEvent.
   * Full reindex is detected by having {@link RootsChangeRescanningInfo#TOTAL_RESCAN} in list
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public @NotNull List<? extends RootsChangeRescanningInfo> getInfos() {
    return myInfos;
  }
}
