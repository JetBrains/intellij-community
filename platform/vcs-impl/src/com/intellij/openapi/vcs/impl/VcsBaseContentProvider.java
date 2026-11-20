// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Can be used to provide "changed lines" in Editor gutter.
 *
 * @see LocalLineStatusTrackerProvider
 * @see VcsBaseContentProviderListener
 */
public interface VcsBaseContentProvider {

  @ApiStatus.Internal
  ProjectExtensionPointName<VcsBaseContentProvider> EP_NAME = new ProjectExtensionPointName<>("com.intellij.vcs.baseContentProvider");

  @Nullable BaseContent getBaseRevision(@NotNull VirtualFile file);

  /**
   * {@link LineStatusTrackerManager} calls the method on the EDT without a read lock, and on a pooled thread.
   * The method must take no lock. Give the last known answer, and report a new answer through {@link VcsBaseContentProviderListener}.
   */
  boolean isSupported(@NotNull VirtualFile file);

  interface BaseContent {

    @NotNull VcsRevisionNumber getRevisionNumber();

    @Nullable String loadContent();
  }
}
