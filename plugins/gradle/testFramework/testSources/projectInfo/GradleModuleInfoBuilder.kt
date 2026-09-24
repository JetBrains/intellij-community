// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.projectInfo

import com.intellij.gradle.testFramework.configuration.TestFilesConfiguration
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

interface GradleModuleInfoBuilder {

  val name: String

  val ideName: String

  val relativePath: String

  val gradleVersion: GradleVersion

  val gradleDsl: GradleDsl

  var groupId: String

  var artifactId: String

  var version: String

  val files: TestFilesConfiguration

  fun sourceSetInfo(sourceSetName: String)
}