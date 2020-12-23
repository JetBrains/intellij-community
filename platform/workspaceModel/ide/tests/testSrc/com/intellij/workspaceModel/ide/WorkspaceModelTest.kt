// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.ProjectTopics
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import junit.framework.Assert.*
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class WorkspaceModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule(forceEnableWorkspaceModel = true)

  @Test
  fun `do not fire rootsChanged if there were no changes`() {
    val disposable = Disposer.newDisposable()
    projectModel.project.messageBus.connect(disposable).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        Assert.fail("rootsChanged must not be called if there are no changes")
      }
    })
    disposable.use {
      runWriteActionAndWait {
        WorkspaceModel.getInstance(projectModel.project).updateProjectModel { }
      }
    }
  }

  @Test
  fun `async model update`() {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val builderSnapshot = model.getBuilderSnapshot()
    builderSnapshot.builder.addModuleEntity("MyModule", emptyList(), object : EntitySource {})

    val replacement = builderSnapshot.getStorageReplacement()

    val updated = runWriteActionAndWait {
      model.replaceProjectModel(replacement)
    }

    assertTrue(updated)

    val moduleEntity = WorkspaceModel.getInstance(projectModel.project).entityStorage.current.entities(ModuleEntity::class.java).single()
    assertEquals("MyModule", moduleEntity.name)
  }

  @Test
  fun `async model update with fail`() {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val builderSnapshot = model.getBuilderSnapshot()
    builderSnapshot.builder.addModuleEntity("MyModule", emptyList(), object : EntitySource {})

    val replacement = builderSnapshot.getStorageReplacement()

    runWriteActionAndWait {
      model.updateProjectModel {
        it.addModuleEntity("AnotherModule", emptyList(), object : EntitySource {})
      }
    }

    val updated = runWriteActionAndWait {
      WorkspaceModel.getInstance(projectModel.project).replaceProjectModel(replacement)
    }

    assertFalse(updated)
  }
}
