// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.testFramework.*
import kotlinx.coroutines.runBlocking
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.DirectoryFileFilter
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.RegexFileFilter
import org.jetbrains.plugins.gradle.testFramework.util.openProjectAsyncAndWait
import org.junit.Test
import org.junit.runners.Parameterized
import java.io.File

class GradleRelativeConfigCalculatingTest : GradleJpsCompilingTestCase() {

  @Test
  fun testGradleRelativeConfigEquality() {
    setupAndBuildProject("first")
    setupAndBuildProject("second")
    assertConfigEquality()
  }

  private fun setupAndBuildProject(subfolderName: String) {
    createProjectSubDir(subfolderName)
    val projectDir = createProjectSubDir("$subfolderName/projectName")
    createProjectSubFile("$subfolderName/projectName/src/main/resources/dir/file.properties")
    createProjectSubFile("$subfolderName/projectName/src/test/resources/dir/file-test.properties")
    createProjectSubFile("$subfolderName/projectName/build.gradle", "apply plugin: 'java'")
    createProjectSubFile("$subfolderName/projectName/settings.gradle", "")
    runBlocking {
      openProjectAsyncAndWait(projectDir)
        .withProjectAsync { myProject = it }
        .useProjectAsync {
          assertModules("projectName", "projectName.main", "projectName.test")
          compileModules("projectName.main", "projectName.test")
          assertCopied("$subfolderName/projectName/out/production/resources/dir/file.properties")
          assertCopied("$subfolderName/projectName/out/test/resources/dir/file-test.properties")
        }
    }
  }

  private fun assertConfigEquality() {
    val buildManager = BuildManager.getInstance()
    requireNotNull(buildManager) { "BuildManager is disposed" }
    val buildSystemDirectory = File(buildManager.getBuildSystemDirectory(myProject).toString())
    require(buildSystemDirectory.exists()) { "compile-server folder does not exists" }
    val dirs = buildSystemDirectory.listFiles()
    require(dirs.size == 2) { "Number of project caches != 2" }
    val firstProjectConfig = getConfigsList(dirs[0]).sorted()
    val secondProjectConfig = getConfigsList(dirs[1]).sorted()
    assertEquals(firstProjectConfig, secondProjectConfig)
  }

  private fun getConfigsList(targetsDir: File): List<String> {
    val configFilter = RegexFileFilter("gradle-resources-(production|test)\\/.*config\\.dat")
    return FileUtils.listFiles(targetsDir, configFilter, DirectoryFileFilter.DIRECTORY)
      .map { it.readText() }
  }

  companion object {

    @JvmStatic
    @Parameterized.Parameters(name = "with Gradle-{0}")
    fun data() = listOf(arrayOf(BASE_GRADLE_VERSION))
  }
}