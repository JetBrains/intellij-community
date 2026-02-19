// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.attach;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.xdebugger.impl.actions.AttachToProcessActionBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface XAttachRecentItemsMatcher {
  @NotNull
  List<? extends AttachToProcessActionBase.AttachToProcessItem> getMatchingAttachItems(@NotNull AttachToProcessActionBase.RecentItem recentItem,
                                                                       @NotNull List<? extends AttachToProcessActionBase.AttachToProcessItem> allItems,
                                                                       boolean isFirstItem,
                                                                       @NotNull Project project,
                                                                       @NotNull UserDataHolder dataHolder);
}