// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.ide.impl.jps.serialization.copyAndLoadProject
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.bridgeEntities.TestModulePropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleTestPropertiesTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `check test properties updated correctly with workspace`() {
    val mainModule = projectModel.createModule() as ModuleBridge

    val testModuleName = "foo.text"
    val testModule = projectModel.createModule(testModuleName) as ModuleBridge
    val testModuleProperties = TestModuleProperties.getInstance(testModule)
    assertNull(testModuleProperties.productionModule)
    assertNull(testModuleProperties.productionModuleName)

    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)

    UsefulTestCase.assertEmpty(workspaceModel.entityStorage.current.entities (TestModulePropertiesEntity::class.java).toList())

    runWriteActionAndWait {
      workspaceModel.updateProjectModel{ builder ->
        val moduleEntity = builder.resolve(testModule.moduleEntityId)
        builder.modifyEntity(moduleEntity!!) {
          this.testProperties = TestModulePropertiesEntity(ModuleId(mainModule.name), Source)
        }
      }
    }

    UsefulTestCase.assertNotEmpty(workspaceModel.entityStorage.current.entities (TestModulePropertiesEntity::class.java).toList())

    assertSame(mainModule, testModuleProperties.productionModule)
    assertEquals(mainModule.name, testModuleProperties.productionModuleName)
  }

  @Test
  fun `check test properties loading`() {
    val mainModuleName = "foo"
    val testModuleName = "foo.test"

    val projectPath = File(PathManagerEx.getCommunityHomePath(), "platform/workspaceModel/jps/tests/testData/serialization/moduleTestProperties")
    val virtualFileUrlManager = VirtualFileUrlManager.getInstance(projectModel.project)
    val storage = copyAndLoadProject(projectPath, virtualFileUrlManager).storage

    val mainModuleEntity = storage.resolve(ModuleId(mainModuleName))
    val testModuleEntity = storage.resolve(ModuleId(testModuleName))
    assertNotNull(mainModuleEntity)
    assertNotNull(testModuleEntity)
    val testProperties = testModuleEntity.testProperties
    assertNotNull(testProperties)
    assertEquals(ModuleId(mainModuleName), testProperties.productionModuleId)
  }
}