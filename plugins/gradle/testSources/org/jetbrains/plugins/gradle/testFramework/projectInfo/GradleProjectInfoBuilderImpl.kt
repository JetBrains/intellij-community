// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectInfo

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

internal class GradleProjectInfoBuilderImpl private constructor(
  override val projectName: String,
  private val projectRelativePath: String,
  private val projectGradleDsl: GradleDsl,
  private val projectRootModule: GradleModuleInfoBuilderImpl,
) : GradleProjectInfoBuilder, GradleModuleInfoBuilder by projectRootModule {

  constructor(name: String, relativePath: String, gradleVersion: GradleVersion, gradleDsl: GradleDsl)
    : this(name, relativePath, gradleDsl, GradleModuleInfoBuilderImpl(name, relativePath, gradleVersion, gradleDsl))

  private val modules = ArrayList<GradleModuleInfo>()

  private val composites = ArrayList<GradleProjectInfo>()

  override fun compositeInfo(
    name: String,
    relativePath: String,
    gradleDsl: GradleDsl?,
    configure: GradleProjectInfoBuilder.() -> Unit,
  ) {
    val compositeRelativePath = "$projectRelativePath/$relativePath"
    val compositeGradleDsl = gradleDsl ?: projectGradleDsl
    val projectInfo = GradleProjectInfoBuilderImpl(name, compositeRelativePath, gradleVersion, compositeGradleDsl)
      .apply(configure)
      .build()
    composites.add(projectInfo)
  }

  override fun moduleInfo(
    ideName: String,
    relativePath: String,
    gradleDsl: GradleDsl?,
    configure: GradleModuleInfoBuilder.() -> Unit,
  ) {
    val moduleRelativePath = "$projectRelativePath/$relativePath"
    val moduleGradleDsl = gradleDsl ?: projectGradleDsl
    val moduleInfo = GradleModuleInfoBuilderImpl(ideName, moduleRelativePath, gradleVersion, moduleGradleDsl)
      .apply(configure)
      .build()
    modules.add(moduleInfo)
  }

  override fun rootModuleInfo(configure: GradleModuleInfoBuilder.() -> Unit) {
    configure()
  }

  fun build(): GradleProjectInfo {
    val rootModule = projectRootModule.build()
    return object : GradleProjectInfo {
      override val projectName = this@GradleProjectInfoBuilderImpl.projectName
      override val projectRelativePath = this@GradleProjectInfoBuilderImpl.projectRelativePath
      override val rootModule = rootModule
      override val modules = listOf(rootModule) + this@GradleProjectInfoBuilderImpl.modules.toList()
      override val composites = this@GradleProjectInfoBuilderImpl.composites.toList()
    }
  }
}