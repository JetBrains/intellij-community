// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render

import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface RootCell {
  data class RealCommit(
    val path: FilePath?
  ): RootCell
  data class NewCommit(
    val includedPaths: List<FilePath>
  ): RootCell
}
