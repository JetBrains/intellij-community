// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.SkipAddingToWatchedRoots
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.testEntities.DependencyItem
import com.intellij.util.indexing.testEntities.ReferredTestEntity
import com.intellij.util.indexing.testEntities.WithReferenceTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import io.kotest.common.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
class WorkspaceFileIndexContributorOnReferenceDependenciesTest {

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val referredTestEntityContributor = ReferredTestEntityContributor()

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var customContentFileSetRoot: VirtualFile
  private lateinit var module: Module
  private lateinit var referenceTestEntity: WithReferenceTestEntity
  private lateinit var referredTestEntity: ReferredTestEntity
  private lateinit var entityRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(referredTestEntityContributor, disposable)
    val model = WorkspaceModel.getInstance(projectModel.project)
    customContentFileSetRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    entityRoot = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(createTempFile("some-file"))!!
    module = projectModel.createModule()
    // initialize index before the test
    runBlocking { readAction { WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot) } }
    // initialize model

    runBlocking {
      model.update("Add reference and referred entity without direct reference") {
        referenceTestEntity = it.addEntity(WithReferenceTestEntity("ReferenceTestEntity",
                                                                   emptyList(),
                                                                   NonPersistentEntitySource))
        referredTestEntity = it.addEntity(ReferredTestEntity("ReferredTestEntity", entityRoot.toVirtualFileUrl(model.getVirtualFileUrlManager()), NonPersistentEntitySource))
      }
    }
  }

  @Test
  fun `check referred test entity contributor called after reference is created`() = runBlocking {
    referredTestEntityContributor.numberOfCalls.set(0)
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }

    assertEquals(1, referredTestEntityContributor.numberOfCalls.get())
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
  }

  @Test
  fun `check referred test entity contributor called only once`() = runBlocking {
    referredTestEntityContributor.numberOfCalls.set(0)
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    assertEquals(1, referredTestEntityContributor.numberOfCalls.get())
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    referredTestEntityContributor.numberOfCalls.set(0)

    // modify an existing WithReferenceTestEntity entity
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf(*references.toTypedArray(), DependencyItem(referredTestEntity.symbolicId))
      }
    }
    assertEquals(0, referredTestEntityContributor.numberOfCalls.get())
  }

  @Test
  fun `check contributor called once on reference added to multiple entities`() = runBlocking {
    referredTestEntityContributor.numberOfCalls.set(0)
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    assertEquals(1, referredTestEntityContributor.numberOfCalls.get())
    referredTestEntityContributor.numberOfCalls.set(0)

    // add new WithReferenceTestEntity
    WorkspaceModel.getInstance(projectModel.project).update("Add one more reference") {
      it.addEntity(WithReferenceTestEntity("Another reference", listOf(DependencyItem(referredTestEntity.symbolicId)), NonPersistentEntitySource))
    }
    // it is not called because its the seconds reference
    assertEquals(0, referredTestEntityContributor.numberOfCalls.get())
  }

  @Test
  fun `check referred test entity contributor does not called if we have at least one reference`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)

    model.update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }

    referredTestEntityContributor.numberOfCalls.set(0)

    WorkspaceModel.getInstance(projectModel.project).update("Add one more reference") {
      it.addEntity(WithReferenceTestEntity("Another reference", listOf(DependencyItem(referredTestEntity.symbolicId)), NonPersistentEntitySource))
    }

    model.update("Remove reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf()
      }
    }
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    assertEquals(0, referredTestEntityContributor.numberOfCalls.get())
  }

  @Test
  fun `check referred test entity contributor called after reference is removed`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)

    model.update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.numberOfCalls.set(0)

    model.update("Remove reference between entities") {
      it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
        references = mutableListOf()
      }
    }
    readAction {
      // entity is in the WorkspaceModel, but its not referenced by any other entity, so it is not in the workspace
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    assertEquals(1, referredTestEntityContributor.numberOfCalls.get())
  }

  // we need SkipAddingToWatchedRoots to pass filter WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors()
  private class ReferredTestEntityContributor : WorkspaceFileIndexContributor<ReferredTestEntity>, SkipAddingToWatchedRoots {
    val numberOfCalls = AtomicInteger(0)

    override val entityClass: Class<ReferredTestEntity>
      get() = ReferredTestEntity::class.java

    override val dependenciesOnOtherEntities: List<DependencyDescription<ReferredTestEntity>>
      get() = listOf(
        DependencyDescription.OnReference(WithReferenceTestEntity::class.java) {
          it.references.asSequence().map { it.reference }
        },
      )

    override fun registerFileSets(entity: ReferredTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      if (storage.referrers(entity.symbolicId, WithReferenceTestEntity::class.java).any()) {
        registrar.registerFileSet(entity.file, WorkspaceFileKind.CUSTOM, entity, null)
        numberOfCalls.incrementAndGet()
      }
    }
  }
}