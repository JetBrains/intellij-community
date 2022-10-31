// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addModuleEntity
import junit.framework.Assert.*
import org.junit.*

class WorkspaceModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

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

  @Test
  fun `exception at event handling not affect storage applying `() {
    val firstModuleName = "MyModule"
    val secondModuleName = "AnotherModule"


    projectModel.project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: VersionedStorageChange) {
        throw IllegalAccessError()
      }
    })

    val model = WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl
    model.userWarningLoggingLevel = true

    runWriteActionAndWait {
      model.updateProjectModel {
        it.addModuleEntity(firstModuleName, emptyList(), object : EntitySource {})
      }
    }

    runWriteActionAndWait {
      model.updateProjectModel {
        it.addModuleEntity(secondModuleName, emptyList(), object : EntitySource {})
      }
    }

    val entities = model.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(2, entities.size)
    assertEquals(setOf(firstModuleName, secondModuleName), entities.map { it.name }.toSet())
  }

  @Test(expected = RuntimeException::class)
  @Ignore
  fun `recursive update`() {
    ApplicationManager.getApplication().runWriteAction {
      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
          println("So much updates")
        }
      }
    }
  }

  @Test(expected = RuntimeException::class)
  @Ignore
  fun `recursive update silent`() {
    (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModelSilent("Test") {
      (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModelSilent("Test") {
        println("So much updates")
      }
    }
  }

  @Test(expected = RuntimeException::class)
  @Ignore
  fun `recursive update mixed 1`() {
    ApplicationManager.getApplication().runWriteAction {
      (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModelSilent("Test") {
        (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModel {
          println("So much updates")
        }
      }
    }
  }

  @Test(expected = RuntimeException::class)
  @Ignore
  fun `recursive update mixed 2`() {
    ApplicationManager.getApplication().runWriteAction {
      (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModel {
        (WorkspaceModel.getInstance(projectModel.project) as WorkspaceModelImpl).updateProjectModelSilent("Test") {
          println("So much updates")
        }
      }
    }
  }
}
