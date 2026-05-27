// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.project.modules
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestEntityStorage
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestEntityStorage.Companion.entityStorage
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectNode.Companion.externalProjectInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@TestApplication
@PerformanceUnitTest
class GradleModuleDataIndexPerformanceTest {

  private val project by projectFixture()

  @ParameterizedTest
  @CsvSource("10000, 10", "1000, 100")
  fun `test performance of GradleModuleDataIndex#findModuleData`(numHolderModules: Int, numSourceSetModules: Int) {
    val entityStorage = entityStorage(project) {
      it.numHolderModules = numHolderModules
      it.numSourceSetModules = numSourceSetModules
    }
    val projectInfo = externalProjectInfo(project) {
      it.numHolderModules = numHolderModules
      it.numSourceSetModules = numSourceSetModules
    }

    val setup = {
      runWriteAction {
        project.workspaceModel.updateProjectModel { storage ->
          storage.replaceBySource({ it == GradleTestEntityStorage.entitySource }, entityStorage)
        }
      }
      ExternalProjectsDataStorage.getInstance(project)
        .update(projectInfo)
    }

    val test = {
      for (module in project.modules) {
        val moduleNode = GradleModuleDataIndex.findModuleNode(module)
        Assertions.assertEquals(module.name, moduleNode?.data?.internalName) {
          "Incorrect ModuleNode found for ${module.name}"
        }
      }
    }

    Benchmark.newBenchmark("$numHolderModules x $numSourceSetModules modules", test)
      .setup(setup)
      .runAsStressTest()
      .start()
  }
}