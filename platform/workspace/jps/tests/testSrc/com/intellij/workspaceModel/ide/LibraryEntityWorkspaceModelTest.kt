// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.update
import com.intellij.testFramework.workspaceModel.updateProjectModel
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LibraryEntityWorkspaceModelTest {
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

  @Test
  fun `rootsChanged is thrown when library table id changes`() = runBlocking {
    val model = projectModel.project.workspaceModel
    val manager = model.getVirtualFileUrlManager()
    model.update { builder ->
      val roots = listOf(
        LibraryRoot(manager.getOrCreateFromUrl("/123"), LibraryRootTypeId.SOURCES),
        LibraryRoot(manager.getOrCreateFromUrl("/321"), LibraryRootTypeId.COMPILED),
      )
      builder addEntity LibraryEntity("MyLib", LibraryTableId.ProjectLibraryTableId, roots, MySource)

      val libraryDependency = LibraryDependency(LibraryId("MyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.RUNTIME)
      builder addEntity ModuleEntity("MyModule", listOf(libraryDependency), MySource) {}
    }

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
      model.updateProjectModel { builder ->
        val library = builder.entities<LibraryEntity>().single()
        builder.modifyLibraryEntity(library) {
          this.tableId = LibraryTableId.ModuleLibraryTableId(ModuleId("MyModule"))
        }
      }
    }

    assertEquals(1, rootsChangedCounter)
    assertFalse(projectModel.project.messageBus.hasUndeliveredEvents(ModuleRootListener.TOPIC))
  }

  @Test
  fun `rootsChanged is not thrown when order of library root groups changes`() = runBlocking {
    val model = projectModel.project.workspaceModel
    val manager = model.getVirtualFileUrlManager()
    model.update { builder ->
      val roots = listOf(
        LibraryRoot(manager.getOrCreateFromUrl("/123"), LibraryRootTypeId.SOURCES),
        LibraryRoot(manager.getOrCreateFromUrl("/321"), LibraryRootTypeId.COMPILED),
      )
      builder addEntity LibraryEntity("MyLib", LibraryTableId.ProjectLibraryTableId, roots, MySource)

      val libraryDependency = LibraryDependency(LibraryId("MyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.RUNTIME)
      builder addEntity ModuleEntity("MyModule", listOf(libraryDependency), MySource) {}
    }

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
      model.updateProjectModel { builder ->
        val library = builder.entities<LibraryEntity>().single()
        builder.modifyLibraryEntity(library) {
          this.roots.reverse()
        }
      }
    }

    assertEquals(LibraryRootTypeId.COMPILED, model.currentSnapshot.entities<LibraryEntity>().single().roots.first().type)

    assertEquals(0, rootsChangedCounter)
    assertFalse(projectModel.project.messageBus.hasUndeliveredEvents(ModuleRootListener.TOPIC))
  }

  @Test
  fun `rootsChanged is thrown when order of roots in one group changes`() = runBlocking {
    val model = projectModel.project.workspaceModel
    val manager = model.getVirtualFileUrlManager()
    model.update { builder ->
      val roots = listOf(
        LibraryRoot(manager.getOrCreateFromUrl("/123"), LibraryRootTypeId.COMPILED),
        LibraryRoot(manager.getOrCreateFromUrl("/321"), LibraryRootTypeId.COMPILED),
      )
      builder addEntity LibraryEntity("MyLib", LibraryTableId.ProjectLibraryTableId, roots, MySource)

      val libraryDependency = LibraryDependency(LibraryId("MyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.RUNTIME)
      builder addEntity ModuleEntity("MyModule", listOf(libraryDependency), MySource) {}
    }

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
      model.updateProjectModel { builder ->
        val library = builder.entities<LibraryEntity>().single()
        builder.modifyLibraryEntity(library) {
          this.roots.reverse()
        }
      }
    }

    assertEquals("/321", model.currentSnapshot.entities<LibraryEntity>().single().roots.first().url.url)

    assertEquals(1, rootsChangedCounter)
    assertFalse(projectModel.project.messageBus.hasUndeliveredEvents(ModuleRootListener.TOPIC))
  }
}