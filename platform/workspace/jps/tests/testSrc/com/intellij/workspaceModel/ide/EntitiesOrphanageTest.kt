// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.runWriteAction
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

@TestApplication
@RunInEdt(writeIntent = true)
class EntitiesOrphanageTest {

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val virtualFileManager: VirtualFileUrlManager
    get() = VirtualFileUrlManager.getInstance(projectModel.project)

  @BeforeEach
  fun setUp() {
    Assumptions.assumeTrue(EntitiesOrphanage.isEnabled)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding content root`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource)
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(url, contentRoots.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding content root to existing one duplicate`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
            }
          }

        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(url, contentRoots.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding content root to existing one`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val url2 = virtualFileManager.fromUrl("/1234")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url2, emptyList(), MySource))
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots
    assertEquals(2, contentRoots.size)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @Test
  fun `adding content root to removed module`() {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
        builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
        }
      }

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

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `do not add orphan content root`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource))
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource)
          }

        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val roots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots
    assertEquals(0, roots.toList().size)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(1, orphanModules.size)
    assertEquals(1, orphanModules.single().contentRoots.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `add content root to existing module`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource)
          }

        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(url, contentRoots.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `add content root to existing module and module remain in orphanage`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val url2 = virtualFileManager.fromUrl("/1233")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(
                ContentRootEntity(url, emptyList(), MySource),
                ContentRootEntity(url2, emptyList(), OrphanageWorkerEntitySource),
              )
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource)
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(url, contentRoots.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(1, orphanModules.size)
    val remainedOrphanUrl = orphanModules.single().contentRoots.single().url
    assertEquals(url2, remainedOrphanUrl)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding source root`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl = virtualFileManager.fromUrl("/123/source")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource))
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val sourceRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single()
    assertEquals(sourceUrl, sourceRoots.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding source root to existing one duplicate`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl = virtualFileManager.fromUrl("/123/source")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource) {
                this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
              })
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val sourceRoot = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots.single()
    assertEquals(sourceUrl, sourceRoot.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding source root to existing one`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl1 = virtualFileManager.fromUrl("/123/source1")
    val sourceUrl2 = virtualFileManager.fromUrl("/123/source2")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.sourceRoots = listOf(SourceRootEntity(sourceUrl1, "", MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.sourceRoots = listOf(SourceRootEntity(sourceUrl2, "", MySource))
              })
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val sourceRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().sourceRoots
    assertEquals(2, sourceRoots.size)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding source root to removed module`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val sourceUrl = virtualFileManager.fromUrl("/123/source1")
    runWriteAction {
      EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
        builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
          })
        }
      }

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

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding exclude root`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val excludeUrl = virtualFileManager.fromUrl("/123/source")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl, MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource))
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val excludeUrls = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().excludedUrls.single()
    assertEquals(excludeUrl, excludeUrls.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding exclude root to existing one duplicate`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val excludeUrl = virtualFileManager.fromUrl("/123/source")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl, MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource) {
                this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl, MySource))
              })
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val myExcludeUrl = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().excludedUrls.single()
    assertEquals(excludeUrl, myExcludeUrl.url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding exclude root to existing one`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val excludeUrl1 = virtualFileManager.fromUrl("/123/exclude1")
    val excludeUrl2 = virtualFileManager.fromUrl("/123/exclude2")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl1, MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl2, MySource))
              })
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val excludedUrls = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single().excludedUrls
    assertEquals(2, excludedUrls.size)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `adding exclude root to removed module`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val excludeUrl = virtualFileManager.fromUrl("/123/source1")
    runWriteAction {
      EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
        builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
          this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
            this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl, MySource))
          })
        }
      }

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

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `move both source and exclude root`(orphanBeforeUpdate: Boolean) {
    val url = virtualFileManager.fromUrl("/123")
    val excludeUrl = virtualFileManager.fromUrl("/123/source1")
    val sourceUrl = virtualFileManager.fromUrl("/123/source2")
    runWriteAction {
      // List of operations as functions to support parametrized test. We call them in different order
      val operations = listOf(
        {
          EntitiesOrphanage.getInstance(projectModel.project).update { builder ->
            builder addEntity ModuleEntity("MyName", emptyList(), OrphanageWorkerEntitySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), OrphanageWorkerEntitySource) {
                this.sourceRoots = listOf(SourceRootEntity(sourceUrl, "", MySource))
                this.excludedUrls = listOf(ExcludeUrlEntity(excludeUrl, MySource))
              })
            }
          }
        },
        {
          WorkspaceModel.getInstance(projectModel.project).updateProjectModel {
            it addEntity ModuleEntity("MyName", emptyList(), MySource) {
              this.contentRoots = listOf(ContentRootEntity(url, emptyList(), MySource))
            }
          }
        }
      )

      if (orphanBeforeUpdate) {
        operations[0]()
        operations[1]()
      }
      else {
        operations[1]()
        operations[0]()
      }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val contentRoots = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
      .entities(ModuleEntity::class.java).single().contentRoots.single()
    assertEquals(excludeUrl, contentRoots.excludedUrls.single().url)
    assertEquals(sourceUrl, contentRoots.sourceRoots.single().url)

    val orphanModules = EntitiesOrphanage.getInstance(projectModel.project).currentSnapshot.entities(ModuleEntity::class.java).toList()
    assertEquals(0, orphanModules.size)
  }
}