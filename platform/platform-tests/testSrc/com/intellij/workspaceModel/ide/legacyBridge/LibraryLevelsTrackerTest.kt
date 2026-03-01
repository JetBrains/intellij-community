// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.LibraryLevelsTracker
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class LibraryLevelsTrackerTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @Test
  fun `check the tracker`() = runBlocking {

    val tracker = LibraryLevelsTracker.getInstance(projectModel.project)

    assertTrue(tracker.getLibraryLevels().isEmpty())

    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("MyLib", LibraryTableId.ProjectLibraryTableId, emptyList(), MySource)
    }

    assertTrue(tracker.getLibraryLevels().isEmpty())
    assertTrue(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))

    projectModel.project.workspaceModel.update {
      val deps = listOf(LibraryDependency(LibraryId("MyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule", deps, MySource)
    }

    assertEquals(LibraryTableId.ProjectLibraryTableId.level, tracker.getLibraryLevels().single())
    assertFalse(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))

    projectModel.project.workspaceModel.update {
      val deps = listOf(LibraryDependency(LibraryId("MyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule2", deps, MySource)
    }

    assertEquals(LibraryTableId.ProjectLibraryTableId.level, tracker.getLibraryLevels().single())
    assertFalse(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))

    projectModel.project.workspaceModel.update {
      it.removeEntity(it.resolve(ModuleId("MyModule2"))!!)
    }

    assertEquals(LibraryTableId.ProjectLibraryTableId.level, tracker.getLibraryLevels().single())
    assertFalse(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))

    projectModel.project.workspaceModel.update {
      it.removeEntity(it.resolve(ModuleId("MyModule"))!!)
    }

    assertTrue(tracker.getLibraryLevels().isEmpty())
    assertTrue(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))
  }

  @Test
  fun `add new modules in batch but remove one by one`() = runBlocking {
    val tracker = LibraryLevelsTracker.getInstance(projectModel.project)

    assertTrue(tracker.getLibraryLevels().isEmpty())

    projectModel.project.workspaceModel.update {
      it addEntity LibraryEntity("MyLib", LibraryTableId.ProjectLibraryTableId, emptyList(), MySource)

      val deps = listOf(LibraryDependency(LibraryId("MyLib", LibraryTableId.ProjectLibraryTableId), false, DependencyScope.TEST))
      it addEntity ModuleEntity("MyModule1", deps, MySource)
      it addEntity ModuleEntity("MyModule2", deps, MySource)
    }

    assertEquals(LibraryTableId.ProjectLibraryTableId.level, tracker.getLibraryLevels().single())
    assertFalse(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))

    projectModel.project.workspaceModel.update {
      it.removeEntity(it.resolve(ModuleId("MyModule1"))!!)
    }

    assertEquals(LibraryTableId.ProjectLibraryTableId.level, tracker.getLibraryLevels().single())
    assertFalse(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))

    projectModel.project.workspaceModel.update {
      it.removeEntity(it.resolve(ModuleId("MyModule2"))!!)
    }

    assertTrue(tracker.getLibraryLevels().isEmpty())
    assertTrue(tracker.isNotUsed(LibraryTableId.ProjectLibraryTableId.level))
  }

  private object MySource : EntitySource
}