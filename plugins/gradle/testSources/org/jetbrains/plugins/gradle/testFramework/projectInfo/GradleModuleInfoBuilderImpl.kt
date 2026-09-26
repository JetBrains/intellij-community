// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfigurationImpl
import java.nio.file.Path
import kotlin.io.path.name

internal class GradleModuleInfoBuilderImpl(
  override val name: String,
  override val ideName: String,
  override val relativePath: String,
  override val gradleVersion: GradleVersion,
  override var gradleDsl: GradleDsl,
) : GradleModuleInfoBuilder {

  constructor(ideName: String, relativePath: String, gradleVersion: GradleVersion, gradleDsl: GradleDsl)
    : this(Path.of(relativePath).normalize().name, ideName, relativePath, gradleVersion, gradleDsl)

  override var groupId: String = "org.example"

  override var artifactId: String = name

  override var version: String = "1.0-SNAPSHOT"

  override val files = TestFilesConfigurationImpl()

  private val sourceSetModules = ArrayList<String>()

  override fun sourceSetInfo(sourceSetName: String) {
    sourceSetModules.add("$ideName.$sourceSetName")
  }

  fun build(): GradleModuleInfo =
    object : GradleModuleInfo {
      override val name: String = this@GradleModuleInfoBuilderImpl.name
      override val ideName: String = this@GradleModuleInfoBuilderImpl.ideName
      override val relativePath: String = this@GradleModuleInfoBuilderImpl.relativePath
      override val gradleDsl: GradleDsl = this@GradleModuleInfoBuilderImpl.gradleDsl
      override val groupId: String = this@GradleModuleInfoBuilderImpl.groupId
      override val artifactId: String = this@GradleModuleInfoBuilderImpl.artifactId
      override val version: String = this@GradleModuleInfoBuilderImpl.version
      override val sourceSetModules: List<String> = this@GradleModuleInfoBuilderImpl.sourceSetModules.toList()
      override val files: TestFilesConfiguration = this@GradleModuleInfoBuilderImpl.files
    }
}