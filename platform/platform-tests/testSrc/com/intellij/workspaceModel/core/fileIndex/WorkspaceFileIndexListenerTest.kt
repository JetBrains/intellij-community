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
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import io.kotest.common.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

@TestApplication
class WorkspaceFileIndexListenerTest {

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val parentWorkspaceFileIndexContributor = ParentWorkspaceFileIndexContributor()

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var customContentFileSetRoot: VirtualFile
  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(parentWorkspaceFileIndexContributor, disposable)
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
    assertEquals(1, listener.storedRoots.size)

    assertEquals(parentEntityRoot,
                 listener.storedRoots.first().root.toVirtualFileUrl(model.getVirtualFileUrlManager()))
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
    assertEquals(1, listener.storedRoots.size)

    val parentEntity = model.currentSnapshot.entities(ParentTestEntity::class.java).first()

    model.update("Remove entity") {storage ->
      storage.removeEntity(parentEntity)
    }

    assertEquals(1, listener.removedRoots.size)
    assertEquals(1, listener.storedRoots.size)
    assertEquals(parentEntityRoot,
                 listener.removedRoots.first().root.toVirtualFileUrl(model.getVirtualFileUrlManager()))

  }

  private class MyWorkspaceFileIndexListener : WorkspaceFileIndexListener {
    val storedRoots = ConcurrentCollectionFactory.createConcurrentSet<WorkspaceFileSet>()
    val removedRoots = ConcurrentCollectionFactory.createConcurrentSet<WorkspaceFileSet>()

    override fun workspaceFileIndexChanged(event: WorkspaceFileIndexChangedEvent) {
      storedRoots.addAll(event.getStoredFileSets())
      removedRoots.addAll(event.getRemovedFileSets())
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
