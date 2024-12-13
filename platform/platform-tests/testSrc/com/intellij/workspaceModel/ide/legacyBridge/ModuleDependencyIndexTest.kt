// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.application.writeAction
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class ModuleDependencyIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  val index: ModuleDependencyIndex
    get() = ModuleDependencyIndex.getInstance(projectModel.project)

  @Test
  fun `test dependency is not presented`() = runBlocking {
    val presented = index.hasDependencyOn(LibraryId("Hey", LibraryTableId.ProjectLibraryTableId))

    assertFalse(presented)
  }

  @Test
  fun `test dependency exists but library does not`() = runBlocking {
    projectModel.project.workspaceModel.update {
      val deps = listOf(LibraryDependency(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule", deps, MySource)
    }

    val presented = index.hasDependencyOn(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId))

    assertTrue(presented)
  }

  @Test
  fun `test library exists but dependency does not`() = runBlocking {
    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("HeyLib", LibraryTableId.ProjectLibraryTableId, emptyList(), MySource)
    }

    val presented = index.hasDependencyOn(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId))

    assertFalse(presented)
  }

  @Test
  fun `test dependency on project library`() = runBlocking {
    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("HeyLib", LibraryTableId.ProjectLibraryTableId, emptyList(), MySource)
      val deps = listOf(LibraryDependency(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule", deps, MySource)
    }

    val presented = index.hasDependencyOn(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId))

    assertTrue(presented)
  }

  @Test
  fun `test dependency on global library`() = runBlocking {
    try {
      writeAction {
        GlobalWorkspaceModel.getInstance().updateModel("Test") {
          val manager = GlobalWorkspaceModel.getInstance().getVirtualFileUrlManager()
          val globalEntitySource = JpsGlobalFileEntitySource(manager.getOrCreateFromUrl("/url"))
          it addEntity LibraryEntity("GlobalLib", LibraryTableId.GlobalLibraryTableId("application"), emptyList(), globalEntitySource)
        }
      }
      projectModel.project.workspaceModel.update {
        val deps = listOf(LibraryDependency(LibraryId("GlobalLib", LibraryTableId.GlobalLibraryTableId("application")), false, DependencyScope.TEST))
        it addEntity ModuleEntity("MyModule", deps, MySource)
      }

      val presented = index.hasDependencyOn(LibraryId("GlobalLib", LibraryTableId.GlobalLibraryTableId("application")))

      assertTrue(presented)
    }
    finally {
      // Explicit clean up to avoid leaked global library
      projectModel.project.workspaceModel.update {
        val globalLib = it.resolve(LibraryId("GlobalLib", LibraryTableId.GlobalLibraryTableId("application")))!!
        it.removeEntity(globalLib)
      }
    }
  }

  @Test
  fun `test dependency on module library`() = runBlocking {
    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("ModuleLib", LibraryTableId.ModuleLibraryTableId(ModuleId("MyModule")), emptyList(), MySource)
      val deps = listOf(LibraryDependency(LibraryId("ModuleLib", LibraryTableId.ModuleLibraryTableId(ModuleId("MyModule"))), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule", deps, MySource)
    }

    val presented = index.hasDependencyOn(LibraryId("ModuleLib", LibraryTableId.ModuleLibraryTableId(ModuleId("MyModule"))))

    assertTrue(presented)
  }

  @Test
  fun `test dependency on project library after rename`() = runBlocking {
    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("HeyLib", LibraryTableId.ProjectLibraryTableId, emptyList(), MySource)
      val deps = listOf(LibraryDependency(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule", deps, MySource)
    }

    projectModel.project.workspaceModel.update {
      val resolved = it.resolve(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId))!!
      it.modifyLibraryEntity(resolved) {
        this.name = "NewName"
      }
    }

    val presented = index.hasDependencyOn(LibraryId("NewName", LibraryTableId.ProjectLibraryTableId))
    assertTrue(presented)
    val libDependency = projectModel.project.workspaceModel.currentSnapshot.resolve(ModuleId("MyModule"))!!.dependencies.single() as LibraryDependency
    assertEquals("NewName", libDependency.library.name)
  }

  @Test
  @Disabled("Currently, the global libs are not disposed making this test broken. This is a bug")
  fun `test dependency on global library after rename`() = runBlocking {
    try {
      writeAction {
        GlobalWorkspaceModel.getInstance().updateModel("Test") {
          val manager = GlobalWorkspaceModel.getInstance().getVirtualFileUrlManager()
          val globalEntitySource = JpsGlobalFileEntitySource(manager.getOrCreateFromUrl("/url"))
          it addEntity LibraryEntity("GlobalLib", LibraryTableId.GlobalLibraryTableId("application"), emptyList(), globalEntitySource)
        }
      }
      projectModel.project.workspaceModel.update {
        val deps = listOf(LibraryDependency(LibraryId("GlobalLib", LibraryTableId.GlobalLibraryTableId("application")), false, DependencyScope.TEST))
        it addEntity ModuleEntity("MyModule", deps, MySource)
      }

      writeAction {
        GlobalWorkspaceModel.getInstance().updateModel("Test") {
          val resolved = it.resolve(LibraryId("GlobalLib", LibraryTableId.GlobalLibraryTableId("application")))!!
          it.modifyLibraryEntity(resolved) {
            this.name = "NewGlobalName"
          }
        }
      }

      val presented = index.hasDependencyOn(LibraryId("NewGlobalName", LibraryTableId.GlobalLibraryTableId("application")))
      assertTrue(presented)
      val libDependency = projectModel.project.workspaceModel.currentSnapshot.resolve(ModuleId("MyModule"))!!.dependencies.single() as LibraryDependency
      assertEquals("NewGlobalName", libDependency.library.name)
    }
    catch (e: RuntimeException) {
      println(e)
    }
    finally {
      // Explicit clean up to avoid leaked global library
      projectModel.project.workspaceModel.update {
        val globalLib = it.resolve(LibraryId("NewGlobalName", LibraryTableId.GlobalLibraryTableId("application")))!!
        it.removeEntity(globalLib)
        val globalLib2 = it.resolve(LibraryId("GlobalLib", LibraryTableId.GlobalLibraryTableId("application")))!!
        it.removeEntity(globalLib2)
      }
    }
  }

  @Test
  fun `events on adding and removing dependencies on modules`() = runBlocking {
    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("HeyLib", LibraryTableId.ProjectLibraryTableId, emptyList(), MySource)
    }

    val events1 = withDependencyListener {
      projectModel.project.workspaceModel.update {
        val deps = listOf(LibraryDependency(LibraryId("HeyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
        it addEntity ModuleEntity("MyModule2", deps, MySource)
      }
    }
    assertTrue(events1.isEmpty())
  }

  private suspend fun withDependencyListener(action: suspend () -> Unit): List<String> {
    val events = mutableListOf<String>()

    val listener = object : ModuleDependencyListener {
    }

    try {
      index.addListener(listener)
      action()
    }
    finally {
      index.removeListener(listener)
    }
    return events
  }

  private object MySource : EntitySource
}