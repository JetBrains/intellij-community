// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GradleCoroutineScopeService(private val coroutineScope: CoroutineScope) {
  companion object {
    val Project.gradleCoroutineScope: CoroutineScope
      get() = service<GradleCoroutineScopeService>().coroutineScope
  }
}