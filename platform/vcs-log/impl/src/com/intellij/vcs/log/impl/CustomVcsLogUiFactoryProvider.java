// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * An extension to replace Vcs Log tabs for specific version control systems.
 * Should only be used by plugins providing support for new version controls.
 *
 * @see VcsLogManager.VcsLogUiFactory
 * @see MainVcsLogUi
 */
@ApiStatus.Experimental
public interface CustomVcsLogUiFactoryProvider {
  ProjectExtensionPointName<CustomVcsLogUiFactoryProvider> LOG_CUSTOM_UI_FACTORY_PROVIDER_EP =
    new ProjectExtensionPointName<>("com.intellij.customVcsLogUiFactoryProvider");

  /**
   * Is this extension active for the specified repository roots and providers.
   *
   * @param providers a map of version control roots to the corresponding log providers
   * @return true if this extension should be used when creating {@link MainVcsLogUi} instances for these roots and providers
   */
  boolean isActive(@NotNull Map<VirtualFile, VcsLogProvider> providers);

  /**
   * Returns a {@link VcsLogManager.VcsLogUiFactory} for creating a single {@link MainVcsLogUi} instance
   * in the Vcs Log with the specified id and filters.
   * Each call of this method should be creating a new factory instance, as factories aren't reusable,
   * and the created instance should not be stored anywhere.
   * Extending {@link VcsLogManager.BaseVcsLogUiFactory} is recommended.
   *
   * @param logId         id of the created Vcs Log tab
   * @param vcsLogManager manager of the Vcs Log to create the tab for
   * @param filters       filters to be set in the created Vcs Log tab
   * @return a factory for creating new Vcs Log tab with the specified parameters
   * @see VcsLogManager.BaseVcsLogUiFactory
   */
  @NotNull VcsLogManager.VcsLogUiFactory<? extends MainVcsLogUi> createLogUiFactory(@NotNull String logId,
                                                                                    @NotNull VcsLogManager vcsLogManager,
                                                                                    @Nullable VcsLogFilterCollection filters);
}
