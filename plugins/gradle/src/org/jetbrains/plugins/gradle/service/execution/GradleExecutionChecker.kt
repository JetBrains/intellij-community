// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.extensions.ExtensionPointName
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GradleExecutionChecker {
  fun checkExecution(context: GradleExecutionContext, buildEnvironment: BuildEnvironment): Unit
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleExecutionChecker> = ExtensionPointName("org.jetbrains.plugins.gradle.executionChecker")
  }
}