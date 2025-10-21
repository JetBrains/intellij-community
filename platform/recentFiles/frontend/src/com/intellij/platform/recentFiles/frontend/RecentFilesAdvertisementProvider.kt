// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface RecentFilesAdvertisementProvider {
  fun getBanner(project: Project): JComponent?

  companion object {
    internal val EP_NAME: ExtensionPointName<RecentFilesAdvertisementProvider> = ExtensionPointName<RecentFilesAdvertisementProvider>(
      "com.intellij.recentFiles.advertisementProvider")
  }
}