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
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
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
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      )

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

  @Test
  fun `adding content root to existing one duplicate`() {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(url, contentRoots.url)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `adding content root to existing one`() {
    val url = virtualFileManager.fromUrl("/123")
    val url2 = virtualFileManager.fromUrl("/1234")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url2, emptyList(), MySource))
        }
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots
    assertEquals(2, contentRoots.size)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `adding content root to removed module`() {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource)
      }

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        val module = it.entities(ModuleEntity::class.java).single()
        it.removeEntity(module)
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val modules = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java)
    assertEquals(0, modules.toList().size)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `do not add orphan content root`() {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource))
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource)
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val roots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots
    assertEquals(0, roots.toList().size)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(1, orphanModules.size)
    assertEquals(1, orphanModules.single().contentRoots.size)
  }

  @Test
  fun `adding source root`() {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl = virtualFileManager.fromUrl("/123/source")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
          })
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource))
        }
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val sourceRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single()
    assertEquals(sourceUrl, sourceRoots.url)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `adding source root to existing one duplicate`() {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl = virtualFileManager.fromUrl("/123/source")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
          })
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
          })
        }
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val sourceRoot = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single()
    assertEquals(sourceUrl, sourceRoot.url)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `adding source root to existing one`() {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl1 = virtualFileManager.fromUrl("/123/source1")
    val sourceUrl2 = virtualFileManager.fromUrl("/123/source2")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl1, "", MySource))
          })
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl2, "", MySource))
          })
        }
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val sourceRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots
    assertEquals(2, sourceRoots.size)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `adding source root to removed module`() {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl = virtualFileManager.fromUrl("/123/source1")
    runWriteAction {
      projectModel.project.workspaceModel.orphanage.put(
        ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
          })
        }
      )

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        it addEntity ModuleEntity("MyName", emptyList(), MySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      }

      WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
        val module = it.entities(ModuleEntity::class.java).single()
        it.removeEntity(module)
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val modules = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java)
    assertEquals(0, modules.toList().size)

    val orphanModules = projectModel.project.workspaceModel.orphanage.entityStorage.current.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }
}