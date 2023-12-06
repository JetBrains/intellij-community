// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle

import com.intellij.ide.CommandLineInspectionProjectConfigurator
import com.intellij.ide.CommandLineInspectionProjectConfigurator.ConfiguratorContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleBundle


class GradleCommandLineProjectConfigurator : CommandLineInspectionProjectConfigurator {
  override fun getName() = "gradle"

  override fun getDescription(): String = GradleBundle.message("gradle.commandline.description")

  override fun configureEnvironment(context: ConfiguratorContext) {
    prepareGradleConfiguratorEnvironment(context.logger)
  }

  override fun configureProject(project: Project, context: ConfiguratorContext): Unit = runBlockingCancellable {
    GradleWarmupConfigurator().runWarmup(project)
  }

}
