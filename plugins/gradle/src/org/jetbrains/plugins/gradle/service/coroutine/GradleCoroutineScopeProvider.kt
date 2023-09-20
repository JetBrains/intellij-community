// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.coroutine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class GradleCoroutineScopeProvider(val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleCoroutineScopeProvider = project.service()
  }
}
