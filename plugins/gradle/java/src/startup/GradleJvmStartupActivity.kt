// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.startup

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager

internal class GradleJvmStartupActivity : ProjectActivity {

  override suspend fun execute(project: Project) {
    blockingContext {
      GradleBuildClasspathManager.getInstance(project).reload()
    }
  }
}
