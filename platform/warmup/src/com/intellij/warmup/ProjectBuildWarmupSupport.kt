// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.extensions.ExtensionPointName
import java.util.concurrent.CompletableFuture

interface ProjectBuildWarmupSupport {
  companion object {
    var EP_NAME = ExtensionPointName<ProjectBuildWarmupSupport>("com.intellij.projectBuildWarmupSupport")
  }

  fun getBuilderId(): String
  fun buildProject(rebuild: Boolean = false): CompletableFuture<Unit>
}