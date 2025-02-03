// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.externalSystem.test.compileModules
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.utils.io.deleteRecursively
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name


abstract class GradleRelativeConfigCalculatingTestCase : GradleTestCase() {

  private lateinit var projectSystemDirectories: MutableList<Path>

  @BeforeEach
  fun setUpGradleRelativeConfigCalculatingTestCase() {
    projectSystemDirectories = ArrayList()
  }

  @AfterEach
  fun tearDownGradleRelativeConfigCalculatingTestCase() {
    runAll(projectSystemDirectories) { directory ->
      directory.deleteRecursively()
    }
  }

  fun compileProject(project: Project) {
    projectSystemDirectories.add(project.getProjectSystemDirectory())

    val projectName = project.name
    val externalProjectPath = project.basePath!!

    val gradleSettings = GradleSettings.getInstance(project)
    val projectSettings = gradleSettings.getLinkedProjectSettings(externalProjectPath)
    projectSettings!!.delegatedBuild = false

    compileModules(project, false, "$projectName.main", "$projectName.test")
  }

  private fun Project.getProjectSystemDirectory(): Path {
    val buildManager = BuildManager.getInstance()
    return buildManager.getProjectSystemDirectory(this).toPath()
  }

  fun Project.getGradleJpsResourceConfigs(): List<Path> {
    return getGradleJpsResourceConfigs("production") + getGradleJpsResourceConfigs("test")
  }

  private fun Project.getGradleJpsResourceConfigs(type: String): List<Path> {
    val resourceDirectory = getProjectSystemDirectory()
      .getResolvedPath("targets/gradle-resources-$type")
    val mainDirectory = resourceDirectory.listDirectoryEntries().single { it.name.startsWith("$name.main") }
    val testDirectory = resourceDirectory.listDirectoryEntries().single { it.name.startsWith("$name.test") }
    val mainConfig = mainDirectory.getResolvedPath("config.dat")
    val testConfig = testDirectory.getResolvedPath("config.dat")
    Assertions.assertTrue(mainConfig.exists(), "File doesn't exists $mainConfig")
    Assertions.assertTrue(testConfig.exists(), "File doesn't exists $testConfig")
    return listOf(mainConfig, testConfig)
  }

  fun assertFileExists(relativePath: String) {
    val path = testRoot.toNioPath().getResolvedPath(relativePath)
    Assertions.assertTrue(path.exists(), "File doesn't exists $path")
    Assertions.assertTrue(path.isRegularFile(), "Path doesn't reference file $path")
  }
}