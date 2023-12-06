// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfigurationImpl
import java.nio.file.Path
import kotlin.io.path.name

data class ModuleInfo(
  val name: String,
  val ideName: String,
  val relativePath: String,
  val useKotlinDsl: Boolean,
  val groupId: String,
  val artifactId: String,
  val version: String,
  val modulesPerSourceSet: List<String>,
  val filesConfiguration: TestFilesConfiguration
) {

  companion object {

    fun create(builder: Builder): ModuleInfo {
      return ModuleInfo(
        builder.name,
        builder.ideName,
        builder.moduleRelativePath,
        builder.useKotlinDsl,
        builder.groupId,
        builder.artifactId,
        builder.version,
        builder.modulesPerSourceSet,
        builder.filesConfiguration
      )
    }

    fun createBuilder(
      ideName: String,
      relativePath: String,
      useKotlinDsl: Boolean,
      configure: Builder.() -> Unit = {}
    ): Builder {
      val builder = BuilderImpl(
        Path.of(relativePath).normalize().name,
        ideName,
        relativePath,
        useKotlinDsl
      )
      builder.configure()
      return builder
    }
  }

  interface Builder {
    val name: String
    val ideName: String
    val moduleRelativePath: String
    var useKotlinDsl: Boolean
    var groupId: String
    var artifactId: String
    var version: String
    val modulesPerSourceSet: MutableList<String>
    val filesConfiguration: TestFilesConfiguration
  }

  private class BuilderImpl(
    override val name: String,
    override val ideName: String,
    override val moduleRelativePath: String,
    override var useKotlinDsl: Boolean
  ) : Builder {

    override var groupId: String = "org.example"

    override var artifactId: String = name

    override var version: String = "1.0-SNAPSHOT"

    override val modulesPerSourceSet = ArrayList<String>()

    override val filesConfiguration = TestFilesConfigurationImpl()

    init {
      modulesPerSourceSet.add("$ideName.main")
      modulesPerSourceSet.add("$ideName.test")
    }
  }
}