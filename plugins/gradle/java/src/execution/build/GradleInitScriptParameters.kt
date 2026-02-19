// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.openapi.module.Module

interface GradleInitScriptParameters {
  val configuration: JavaRunConfigurationBase
  val module: Module
  val workingDirectory: String?
  val params: String
  val definitions: String
  val gradleTaskPath: String
  val runAppTaskName: String
  val mainClass: String
  val javaExePath: String
  val sourceSetName: String
  val javaModuleName: String?
}