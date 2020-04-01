// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface CustomVcsLogUiFactoryProvider {
  ProjectExtensionPointName<CustomVcsLogUiFactoryProvider> LOG_CUSTOM_UI_FACTORY_PROVIDER_EP =
    new ProjectExtensionPointName<>("com.intellij.customVcsLogUiFactoryProvider");

  boolean isActive(@NotNull VcsLogManager vcsLogManager);

  @NotNull VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> createLogUiFactory(@NotNull String logId,
                                                                                    @NotNull VcsLogManager vcsLogManager,
                                                                                    @Nullable VcsLogFilterCollection filters);
}
