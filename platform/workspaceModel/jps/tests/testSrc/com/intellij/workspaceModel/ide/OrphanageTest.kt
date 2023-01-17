// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

@RunInEdt
@TestApplication
class OrphanageTest {

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val virtualFileManager: VirtualFileUrlManager
    get() = VirtualFileUrlManager.getInstance(projectModel.project)

  @Test
  fun `adding content root`() {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      WorkspaceModel.getInstance(projectModel.project).orphanage.update {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      }

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource)
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(url, contentRoots.url)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }
}