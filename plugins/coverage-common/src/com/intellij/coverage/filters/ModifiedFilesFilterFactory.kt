// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.filters

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModifiedFilesFilterFactory {
  fun createFilter(project: Project): ModifiedFilesFilter?

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<ModifiedFilesFilterFactory> = ExtensionPointName.create("com.intellij.coverageModifiedFilesFilterFactory")
  }
}
