// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface ProjectStorePathCustomizer {

  fun getStoreDirectoryPath(projectRoot: Path): Path?

  companion object {

    val EP_NAME: ExtensionPointName<ProjectStorePathCustomizer> =
      ExtensionPointName.create("com.intellij.projectStorePathCustomizer")
  }
}