// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.facet.mock.AnotherMockFacetType
import com.intellij.facet.mock.MockFacetType
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.FacetEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import org.junit.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FacetWorkspaceModelTest {
  companion object {
    @ClassRule
    @JvmField
    val application = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Before
  fun registerFacetType() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposableRule.disposable) {}
    com.intellij.facet.mock.registerFacetType(MockFacetType(), disposableRule.disposable)
    com.intellij.facet.mock.registerFacetType(AnotherMockFacetType(), disposableRule.disposable)
  }

  @Test
  fun `rootsChanged is not thrown when adding a facet`() {
    projectModel.createModule()
    var rootsChangedCounter = 0
    projectModel.project
      .getMessageBus()
      .connect(disposableRule.disposable)
      .subscribe<ModuleRootListener>(
        ModuleRootListener.TOPIC,
        object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) {
            rootsChangedCounter += 1
          }
        }
      )
    runWriteActionAndWait {
      projectModel.project.workspaceModel.updateProjectModel { builder ->
        val moduleEntity = builder.entities(ModuleEntity::class.java).first()
        builder.modifyModuleEntity(moduleEntity) {
         this.facets += FacetEntity.invoke(moduleEntity.symbolicId, "myName", MOCK_FACET_TYPE_ID, moduleEntity.entitySource)
        }
      }
    }

    assertEquals(0, rootsChangedCounter, "rootsChanged must not be called on change of facets")
    assertFalse(projectModel.project.messageBus.hasUndeliveredEvents(ModuleRootListener.TOPIC))
  }

  @Test
  fun `rootsChanged is not thrown when adding a facet as modifying the module`() {
    projectModel.createModule()
    var rootsChangedCounter = 0
    projectModel.project
      .getMessageBus()
      .connect(disposableRule.disposable)
      .subscribe<ModuleRootListener>(
        ModuleRootListener.TOPIC,
        object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) {
            rootsChangedCounter += 1
          }
        }
      )
    runWriteActionAndWait {
      projectModel.project.workspaceModel.updateProjectModel { builder ->
        val moduleEntity = builder.entities(ModuleEntity::class.java).first()
        Assert.assertTrue(moduleEntity.facets.isEmpty())
        builder.modifyModuleEntity(moduleEntity) {
          this.facets = listOf(FacetEntity.invoke(moduleEntity.symbolicId, "myName", MOCK_FACET_TYPE_ID, moduleEntity.entitySource))
        }
      }
    }

    assertEquals(0, rootsChangedCounter, "rootsChanged must not be called on change of facets")
    assertFalse(projectModel.project.messageBus.hasUndeliveredEvents(ModuleRootListener.TOPIC))
  }
}