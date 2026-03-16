// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.performance.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

internal object GradleJavaPerfCoroutineScope {

  @Service(Service.Level.PROJECT)
  private class ProjectService(val coroutineScope: CoroutineScope)

  val Project.gradlePerfCoroutineScope: CoroutineScope
    get() = service<ProjectService>().coroutineScope
}
