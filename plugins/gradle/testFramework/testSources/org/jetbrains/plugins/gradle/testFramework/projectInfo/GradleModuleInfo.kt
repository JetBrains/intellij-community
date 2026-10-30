// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration

interface GradleModuleInfo {

  val name: String

  val ideName: String

  val relativePath: String

  val gradleDsl: GradleDsl

  val groupId: String

  val artifactId: String

  val version: String

  val sourceSetModules: List<String>

  val files: TestFilesConfiguration
}