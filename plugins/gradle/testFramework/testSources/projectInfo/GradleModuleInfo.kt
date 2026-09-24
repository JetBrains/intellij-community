// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.projectInfo

import com.intellij.gradle.testFramework.configuration.TestFilesConfiguration
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

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