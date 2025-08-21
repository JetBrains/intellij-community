// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.openapi.project.modules
import com.intellij.testFramework.useProjectAsync
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GradleModuleDataFinderPerformanceTest : GradleTestCase() {

  @ParameterizedTest
  @CsvSource("1000")
  fun `test performance of CachedModuleDataFinder#findModuleData`(numModules: Int) {
    val projectInfo = projectInfo("project", GradleDsl.GROOVY) {
      withFile("gradle.properties", "org.gradle.jvmargs=-Xmx10G")
      withFile("gradle.modules", buildString {
        repeat(numModules) {
          append("module-$it").append("\n")
        }
      })
      withSettingsFile {
        setProjectName("project")
        addCode("""
          |new File("gradle.modules").eachLine { moduleName ->
          |  if (!moduleName.isEmpty()) {
          |    include(moduleName)
          |  }
          |}
        """.trimMargin())
      }
      withBuildFile { withJavaPlugin() }
      repeat(numModules) {
        moduleInfo("project.module-$it", "module-$it") {
          withBuildFile { withJavaPlugin() }
        }
      }
    }

    runBlocking {
      initProject(projectInfo)
      openProject("project").useProjectAsync { project ->
        assertProjectStructure(project, projectInfo)
        Benchmark.newBenchmark("CachedModuleDataFinderPerformanceTest CachedModuleDataFinder#findModuleData($numModules)") {
          for (module in project.modules) {
            val moduleData = CachedModuleDataFinder.findModuleData(module)!!
            Assertions.assertEquals(module.name, moduleData.data.internalName)
          }
        }.setup {
          runBlocking {
            reloadProject(project, "project")
          }
        }
          .runAsStressTest()
          .start()
      }
    }
  }
}