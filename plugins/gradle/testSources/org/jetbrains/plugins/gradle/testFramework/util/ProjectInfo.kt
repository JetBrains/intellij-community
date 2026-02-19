// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl

data class ProjectInfo(
  val name: String,
  val relativePath: String,
  val gradleDsl: GradleDsl,
  val rootModule: ModuleInfo,
  val modules: List<ModuleInfo>,
  val composites: List<ProjectInfo>
) {

  companion object {

    fun create(
      name: String,
      relativePath: String,
      gradleDsl: GradleDsl,
      configure: Builder.() -> Unit = {}
    ): ProjectInfo {
      val builder = BuilderImpl(name, relativePath, gradleDsl)
      builder.configure()
      val rootModule = ModuleInfo.create(builder)
      builder.modules.add(rootModule)
      return ProjectInfo(
        builder.projectName,
        builder.projectRelativePath,
        rootModule.gradleDsl,
        rootModule,
        builder.modules,
        builder.composites
      )
    }
  }

  interface Builder : ModuleInfo.Builder {

    fun compositeInfo(
      name: String,
      relativePath: String,
      gradleDsl: GradleDsl? = null,
      configure: Builder.() -> Unit = {}
    ): ProjectInfo

    fun moduleInfo(
      ideName: String,
      relativePath: String,
      gradleDsl: GradleDsl? = null,
      configure: ModuleInfo.Builder.() -> Unit = {}
    ): ModuleInfo
  }

  private class BuilderImpl(
    val projectName: String,
    val projectRelativePath: String,
    val defaultGradleDsl: GradleDsl,
  ) : Builder, ModuleInfo.Builder by ModuleInfo.createBuilder(
    ideName = projectName,
    relativePath = projectRelativePath,
    gradleDsl = defaultGradleDsl
  ) {

    val modules = ArrayList<ModuleInfo>()

    val composites = ArrayList<ProjectInfo>()

    override fun compositeInfo(
      name: String,
      relativePath: String,
      gradleDsl: GradleDsl?,
      configure: Builder.() -> Unit
    ): ProjectInfo {
      val projectInfo = create(
        name,
        "$projectRelativePath/$relativePath",
        gradleDsl ?: defaultGradleDsl,
        configure
      )
      composites.add(projectInfo)
      return projectInfo
    }

    override fun moduleInfo(
      ideName: String,
      relativePath: String,
      gradleDsl: GradleDsl?,
      configure: ModuleInfo.Builder.() -> Unit
    ): ModuleInfo {
      val builder = ModuleInfo.createBuilder(
        ideName,
        "$projectRelativePath/$relativePath",
        gradleDsl ?: defaultGradleDsl,
        configure
      )
      val moduleInfo = ModuleInfo.create(builder)
      modules.add(moduleInfo)
      return moduleInfo
    }
  }
}
