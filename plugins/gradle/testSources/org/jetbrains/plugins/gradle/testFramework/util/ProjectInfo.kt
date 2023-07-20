// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

data class ProjectInfo(
  val name: String,
  val relativePath: String,
  val useKotlinDsl: Boolean,
  val rootModule: ModuleInfo,
  val modules: List<ModuleInfo>,
  val composites: List<ProjectInfo>
) {

  companion object {

    fun create(
      name: String,
      relativePath: String,
      useKotlinDsl: Boolean,
      configure: Builder.() -> Unit = {}
    ): ProjectInfo {
      val builder = BuilderImpl(name, relativePath, useKotlinDsl)
      builder.configure()
      val rootModule = ModuleInfo.create(builder)
      builder.modules.add(rootModule)
      return ProjectInfo(
        builder.projectName,
        builder.projectRelativePath,
        rootModule.useKotlinDsl,
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
      useKotlinDsl: Boolean? = null,
      configure: Builder.() -> Unit = {}
    ): ProjectInfo

    fun moduleInfo(
      ideName: String,
      relativePath: String,
      useKotlinDsl: Boolean? = null,
      configure: ModuleInfo.Builder.() -> Unit = {}
    ): ModuleInfo
  }

  private class BuilderImpl(
    val projectName: String,
    val projectRelativePath: String,
    val defaultUseKotlinDsl: Boolean,
  ) : Builder, ModuleInfo.Builder by ModuleInfo.createBuilder(
    ideName = projectName,
    relativePath = projectRelativePath,
    useKotlinDsl = defaultUseKotlinDsl
  ) {

    val modules = ArrayList<ModuleInfo>()

    val composites = ArrayList<ProjectInfo>()

    override fun compositeInfo(
      name: String,
      relativePath: String,
      useKotlinDsl: Boolean?,
      configure: Builder.() -> Unit
    ): ProjectInfo {
      val projectInfo = create(
        name,
        "$projectRelativePath/$relativePath",
        useKotlinDsl ?: defaultUseKotlinDsl,
        configure
      )
      composites.add(projectInfo)
      return projectInfo
    }

    override fun moduleInfo(
      ideName: String,
      relativePath: String,
      useKotlinDsl: Boolean?,
      configure: ModuleInfo.Builder.() -> Unit
    ): ModuleInfo {
      val builder = ModuleInfo.createBuilder(
        ideName,
        "$projectRelativePath/$relativePath",
        useKotlinDsl ?: defaultUseKotlinDsl,
        configure
      )
      val moduleInfo = ModuleInfo.create(builder)
      modules.add(moduleInfo)
      return moduleInfo
    }
  }
}
