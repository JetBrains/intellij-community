// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.externalSystem.service.project.ExternalSystemModuleDataIndex
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.testFramework.project
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.dataNode.GradleSourceSet.Companion.gradleSourceSet
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Test

class GradleModuleDataIndexTest : GradleModuleDataIndexTestCase() {

  @Test
  fun `test module data finding`(): Unit = runBlocking {
    val settings = GradleSettings.getInstance(project)
    settings.linkedProjectsSettings = listOf(
      GradleProjectSettings().also {
        it.externalProjectPath = "$projectPath/project1"
      }
    )

    val dataStorage = createDataStorage(
      project(name = "project1", projectPath = "$projectPath/project1", systemId = GradleConstants.SYSTEM_ID) {
        module(name = "project1", externalProjectPath = projectPath) {
          gradleSourceSet("main")
          gradleSourceSet("test")
        }
        module(name = "project1.module1", externalProjectPath = "$projectPath/module1") {
          gradleSourceSet("main")
          gradleSourceSet("test")
        }
        module(name = "project1.module2", externalProjectPath = "$projectPath/module2") {
          gradleSourceSet("main")
          gradleSourceSet("test")
        }
      }
    )

    project.replaceService(ExternalProjectsDataStorage::class.java, dataStorage, asDisposable())

    project.workspaceModel.update("Test description") { storage ->
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.main", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1:main"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1"
        externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.test", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1:test"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1"
        externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module1", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1.module1"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module1"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module1.main", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1.module1:main"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module1"
        externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module1.test", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1.module1:test"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module1"
        externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module2", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1.module2"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module2"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module2.main", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1.module2:main"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module2"
        externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module2.test", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1.module2:test"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module2"
        externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
      }
    }

    assertModules(
      "project1", "project1.main", "project1.test",
      "project1.module1", "project1.module1.main", "project1.module1.test",
      "project1.module2", "project1.module2.main", "project1.module2.test",
    )

    assertModuleNode("project1", "$projectPath/project1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1")))
    assertModuleNode("project1", "$projectPath/project1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.main")))
    assertModuleNode("project1", "$projectPath/project1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.test")))
    assertModuleNode("project1.module1", "$projectPath/project1/module1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module1")))
    assertModuleNode("project1.module1", "$projectPath/project1/module1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module1.main")))
    assertModuleNode("project1.module1", "$projectPath/project1/module1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module1.test")))
    assertModuleNode("project1.module2", "$projectPath/project1/module2", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module2")))
    assertModuleNode("project1.module2", "$projectPath/project1/module2", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module2.main")))
    assertModuleNode("project1.module2", "$projectPath/project1/module2", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module2.test")))

    assertModuleNode("project1", "$projectPath/project1", GradleModuleDataIndex.findModuleNode(getModule("project1")))
    assertModuleNode("project1.main", "$projectPath/project1", GradleModuleDataIndex.findModuleNode(getModule("project1.main")))
    assertModuleNode("project1.test", "$projectPath/project1", GradleModuleDataIndex.findModuleNode(getModule("project1.test")))
    assertModuleNode("project1.module1", "$projectPath/project1/module1", GradleModuleDataIndex.findModuleNode(getModule("project1.module1")))
    assertModuleNode("project1.module1.main", "$projectPath/project1/module1", GradleModuleDataIndex.findModuleNode(getModule("project1.module1.main")))
    assertModuleNode("project1.module1.test", "$projectPath/project1/module1", GradleModuleDataIndex.findModuleNode(getModule("project1.module1.test")))
    assertModuleNode("project1.module2", "$projectPath/project1/module2", GradleModuleDataIndex.findModuleNode(getModule("project1.module2")))
    assertModuleNode("project1.module2.main", "$projectPath/project1/module2", GradleModuleDataIndex.findModuleNode(getModule("project1.module2.main")))
    assertModuleNode("project1.module2.test", "$projectPath/project1/module2", GradleModuleDataIndex.findModuleNode(getModule("project1.module2.test")))
  }
}