// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@ApiStatus.Internal
interface ProjectStorePathCustomizer {
  @Internal
  data class StoreDescriptor(
    // project dir as passed to setPath if dir (for example, for bazel it is BUILD.bazel, for JPS, it is a parent of .idea)
    val projectIdentityDir: Path,
    // where we do store project files (misc.xml and so on), for historical reasons, it must be named as `.idea`
    val dotIdea: Path?,
    // project dir as it is expected by user (e.g. parent of BUILD.bazel)
    val historicalProjectBasePath: Path,
  )

  fun getStoreDirectoryPath(projectRoot: Path): StoreDescriptor?

  companion object {
    val EP_NAME: ExtensionPointName<ProjectStorePathCustomizer> = ExtensionPointName("com.intellij.projectStorePathCustomizer")
  }
}