// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetModel

import com.intellij.gradle.toolingExtension.impl.modelSerialization.ToolingSerializer
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleSourceSetModel
import org.junit.jupiter.api.Test
import java.io.File

class GradleSourceSetSerialisationServiceTest {

  @Test
  fun `serializes empty source set model`() {
    val model = DefaultGradleSourceSetModel()

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(model), GradleSourceSetModel::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(model)
  }

  @Test
  fun `serializes source set model`() {
    val mainJavaSources = DefaultExternalSourceDirectorySet().also {
      it.name = "java"
      it.srcDirs = linkedSetOf(File("src/main/java"))
      it.gradleOutputDirs = listOf(File("build/classes/java/main"))
      it.outputDir = File("out/main/java")
      it.isCompilerOutputPathInherited = false
      it.includes = linkedSetOf("**/*.java")
      it.excludes = linkedSetOf("**/generated/**")
      it.filters = listOf(DefaultExternalFilter().also { filter ->
        filter.filterType = "ReplaceTokens"
        filter.propertiesAsJsonMap = "{\"sourceSet\":\"main\"}"
      })
    }
    val mainResources = DefaultExternalSourceDirectorySet().also {
      it.name = "resources"
      it.srcDirs = linkedSetOf(File("src/main/resources"))
      it.gradleOutputDirs = listOf(File("build/resources/main"))
      it.outputDir = File("out/main/resources")
      it.isCompilerOutputPathInherited = false
      it.includes = linkedSetOf("**/*")
      it.excludes = linkedSetOf("**/excluded/**")
      it.filters = emptyList()
    }
    val mainSourceSet = DefaultExternalSourceSet().also {
      it.name = "main"
      it.javaToolchainHome = File("jdks/temurin-21")
      it.sourceCompatibility = "17"
      it.targetCompatibility = "17"
      it.compilerArguments = listOf("-Xlint:all", "-parameters")
      it.artifacts = listOf(File("build/classes/main"), File("build/resources/main"))
      it.sources = linkedMapOf(
        ExternalSystemSourceType.SOURCE to mainJavaSources,
        ExternalSystemSourceType.RESOURCE to mainResources,
      )
    }
    val testSourceSet = DefaultExternalSourceSet().also {
      it.name = "test"
      it.javaToolchainHome = File("jdks/temurin-21")
      it.sourceCompatibility = "17"
      it.targetCompatibility = "17"
      it.compilerArguments = listOf("-Xlint:all")
      it.artifacts = listOf(File("build/classes/test"))
      it.sources = emptyMap()
    }
    val model = DefaultGradleSourceSetModel().also {
      it.toolchainVersion = 21
      it.sourceCompatibility = "17"
      it.targetCompatibility = "17"
      it.taskArtifacts = listOf(File("build/libs/root.jar"), File("build/libs/root-tests.jar"))
      it.configurationArtifacts = linkedMapOf(
        "compileClasspath" to linkedSetOf(File("repo/compile/compile.jar")),
        "runtimeClasspath" to linkedSetOf(File("repo/runtime/runtime.jar"), File("repo/runtime/runtime-helper.jar")),
      )
      it.sourceSets = linkedMapOf(
        "main" to mainSourceSet,
        "test" to testSourceSet,
      )
      it.additionalArtifacts = listOf(File("build/libs/root-sources.jar"))
    }

    val serializer = ToolingSerializer()
    val deserialized = serializer.read(serializer.write(model), GradleSourceSetModel::class.java)

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(model)
  }
}
