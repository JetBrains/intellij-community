// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.SkipAddingToWatchedRoots
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.testEntities.IndexableKind2FileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.IndexingTestEntity2
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

@TestApplication
class WorkspaceFileIndexListenerTest {

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var customContentFileSetRoot: VirtualFile
  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ParentWorkspaceFileIndexContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKind2FileSetTestContributor(), disposable)
    customContentFileSetRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    module = projectModel.createModule()


    runBlocking { readAction { WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(customContentFileSetRoot) } }
  }


  @Test
  fun `listener event for registered file set`() = runBlocking {
    val listener = MyWorkspaceFileIndexListener()
    projectModel.project.messageBus.connect().subscribe(WorkspaceFileIndexListener.TOPIC, listener)

    val model = WorkspaceModel.getInstance(projectModel.project)

    val parentEntityRoot = customContentFileSetRoot.toVirtualFileUrl(model.getVirtualFileUrlManager())
    val parent = ParentTestEntity("parent property",
                                  parentEntityRoot,
                                  NonPersistentEntitySource)

    model.update("Add entity") {
      it.addEntity(parent)
    }

    assertEquals(0, listener.removedRoots.size)
    assertEquals(1, listener.registeredFileSets.size)

    assertEquals(parentEntityRoot,
                 listener.registeredFileSets.first().root.toVirtualFileUrl(model.getVirtualFileUrlManager()))
  }


  @Test
  fun `listener event for removed file set`() = runBlocking {
    val listener = MyWorkspaceFileIndexListener()
    projectModel.project.messageBus.connect().subscribe(WorkspaceFileIndexListener.TOPIC, listener)

    val model = WorkspaceModel.getInstance(projectModel.project)

    val parentEntityRoot = customContentFileSetRoot.toVirtualFileUrl(model.getVirtualFileUrlManager())
    val parent = ParentTestEntity("parent property",
                                  parentEntityRoot,
                                  NonPersistentEntitySource)

    model.update("Add parent") {
      it.addEntity(parent)
    }
    assertEquals(0, listener.removedRoots.size)
    assertEquals(1, listener.registeredFileSets.size)

    val parentEntity = model.currentSnapshot.entities(ParentTestEntity::class.java).first()

    model.update("Remove entity") {storage ->
      storage.removeEntity(parentEntity)
    }

    assertEquals(1, listener.removedRoots.size)
    assertEquals(1, listener.registeredFileSets.size)
    assertEquals(parentEntityRoot,
                 listener.removedRoots.first().root.toVirtualFileUrl(model.getVirtualFileUrlManager()))

  }

  @Test
  fun `events are deduplicated`() = runBlocking {
    val listener = MyWorkspaceFileIndexListener()
    projectModel.project.messageBus.connect().subscribe(WorkspaceFileIndexListener.TOPIC, listener)

    val model = WorkspaceModel.getInstance(projectModel.project)

    val indexable1 = projectModel.baseProjectDir.newVirtualDirectory("indexable1").toVirtualFileUrl(model.getVirtualFileUrlManager())
    val indexable2 = projectModel.baseProjectDir.newVirtualDirectory("indexable2").toVirtualFileUrl(model.getVirtualFileUrlManager())
    val indexable3 = projectModel.baseProjectDir.newVirtualDirectory("indexable3").toVirtualFileUrl(model.getVirtualFileUrlManager())

    model.update("Add IndexingTestEntity with two roots") {
      it.addEntity(IndexingTestEntity(listOf(indexable1, indexable2), emptyList(), NonPersistentEntitySource))
    }
    assertEquals(0, listener.removedRoots.size)
    assertEquals(2, listener.registeredFileSets.size)

    listener.clear()

    model.update("Replace existing IndexingTestEntity with a new one but with different roots") { storage ->
      val replacement = MutableEntityStorage.create()
      replacement addEntity IndexingTestEntity(listOf(indexable2, indexable3), emptyList(), NonPersistentEntitySource)
      storage.replaceBySource({ it is NonPersistentEntitySource}, replacement)
    }

    assertEquals(1, listener.removedRoots.size) // indexable1 was removed
    assertEquals(1, listener.registeredFileSets.size) // only indexable3 was added, indexable2 stayed the same
  }


  @Test
  fun `events from different entity types are not deduplicated`() = runBlocking {
    val listener = MyWorkspaceFileIndexListener()
    projectModel.project.messageBus.connect().subscribe(WorkspaceFileIndexListener.TOPIC, listener)

    val model = WorkspaceModel.getInstance(projectModel.project)

    val indexable1 = projectModel.baseProjectDir.newVirtualDirectory("indexable1").toVirtualFileUrl(model.getVirtualFileUrlManager())
    val indexable2 = projectModel.baseProjectDir.newVirtualDirectory("indexable2").toVirtualFileUrl(model.getVirtualFileUrlManager())
    val indexable3 = projectModel.baseProjectDir.newVirtualDirectory("indexable3").toVirtualFileUrl(model.getVirtualFileUrlManager())

    model.update("Add IndexingTestEntity with two roots") {
      it.addEntity(IndexingTestEntity(listOf(indexable1, indexable2), emptyList(), NonPersistentEntitySource))
    }
    assertEquals(0, listener.removedRoots.size)
    assertEquals(2, listener.registeredFileSets.size)

    listener.clear()

    model.update("Replace IndexingTestEntity with IndexingTestEntity2") { storage ->
      val replacement = MutableEntityStorage.create()
      replacement addEntity IndexingTestEntity2(listOf(indexable2, indexable3), emptyList(), NonPersistentEntitySource)
      storage.replaceBySource({ it is NonPersistentEntitySource}, replacement)
    }

    assertEquals(2, listener.removedRoots.size)
    assertEquals(2, listener.registeredFileSets.size)
  }

  private class MyWorkspaceFileIndexListener : WorkspaceFileIndexListener {
    val registeredFileSets = ConcurrentCollectionFactory.createConcurrentSet<WorkspaceFileSet>()
    val removedRoots = ConcurrentCollectionFactory.createConcurrentSet<WorkspaceFileSet>()

    override fun workspaceFileIndexChanged(event: WorkspaceFileIndexChangedEvent) {
      event.registeredFileSets.forEach {
        registeredFileSets.add(it)
      }
      event.removedFileSets.forEach {
        removedRoots.add(it)
      }
    }

    fun clear() {
      registeredFileSets.clear()
      removedRoots.clear()
    }
  }

  // we need SkipAddingToWatchedRoots to pass filter WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors()
  private class ParentWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ParentTestEntity>, SkipAddingToWatchedRoots {

    override val entityClass: Class<ParentTestEntity>
      get() = ParentTestEntity::class.java

    override fun registerFileSets(entity: ParentTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerFileSet(entity.parentEntityRoot, WorkspaceFileKind.CONTENT, entity, null)
    }
  }
}
