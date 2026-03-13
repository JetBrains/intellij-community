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
import com.intellij.util.indexing.testEntities.OneMoreWithReferenceTestEntity
import com.intellij.util.indexing.testEntities.ReferredTestEntity
import com.intellij.util.indexing.testEntities.ReferredTestEntityBuilder
import com.intellij.util.indexing.testEntities.ReferredTestEntityId
import com.intellij.util.indexing.testEntities.WithReferenceTestEntity
import com.intellij.util.indexing.testEntities.WithReferenceTestEntityBuilder
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import kotlinx.coroutines.runBlocking
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
    referredTestEntityContributor.numberOfCalls.set(0)
  }

  @Test
  fun `check referred test entity contributor called after reference is created`() = runBlocking {
    readAction {
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }

    referredTestEntityContributor.assertNumberOfCalls(2)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
  }

  @Test
  fun `check referred test entity contributor called only once`() = runBlocking {
    readAction {
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.assertNumberOfCalls(2)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    // modify an existing WithReferenceTestEntity entity
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(*references.toTypedArray(), DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.assertNumberOfCalls(0)
  }

  @Test
  fun `check contributor called once on reference added to multiple entities`() = runBlocking {
    WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.assertNumberOfCalls(2)

    // add new WithReferenceTestEntity
    WorkspaceModel.getInstance(projectModel.project).update("Add one more reference") {
      it.addEntity(WithReferenceTestEntity("Another reference", listOf(DependencyItem(referredTestEntity.symbolicId)), NonPersistentEntitySource))
    }
    // it is not called because its the seconds reference
    referredTestEntityContributor.assertNumberOfCalls(0)
  }

  @Test
  fun `check referred test entity contributor does not called if we have at least one reference`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    readAction {
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    model.update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }

    referredTestEntityContributor.numberOfCalls.set(0)

    WorkspaceModel.getInstance(projectModel.project).update("Add one more reference") {
      it.addEntity(WithReferenceTestEntity("Another reference", listOf(DependencyItem(referredTestEntity.symbolicId)), NonPersistentEntitySource))
    }

    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    model.update("Remove reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf()
      }
    }
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    referredTestEntityContributor.assertNumberOfCalls(0)
  }

  @Test
  fun `check referred test entity contributor called after reference is removed`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    readAction {
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    model.update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.numberOfCalls.set(0)

    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    model.update("Remove reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf()
      }
    }
    readAction {
      // entity is in the WorkspaceModel, but its not referenced by any other entity, so it is not in the workspace
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
    referredTestEntityContributor.assertNumberOfCalls(2)
  }

  @Test
  fun `check contributor is called on referred entity rename`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    model.update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.numberOfCalls.set(0)
    model.update("Rename entity and update reference") {
      it.modifyEntity(ReferredTestEntityBuilder::class.java, referredTestEntity) {
        name = "New Name"
      }
    }
    referredTestEntityContributor.assertNumberOfCalls(4)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
  }

  @Test
  fun `check contributor does not called on referred entity rename and rename back`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    model.update("Create reference between entities") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    referredTestEntityContributor.numberOfCalls.set(0)


    (model as WorkspaceModelImpl).updateUnderWriteAction("Rename entity and rename it back") {
      val newEntity = it.modifyEntity(ReferredTestEntityBuilder::class.java, referredTestEntity) {
        name = "New Name"
      }
      it.modifyEntity(ReferredTestEntityBuilder::class.java, newEntity) {
        name = "ReferredTestEntity"
      }
    }
    // now we call it once for "New Name" entity change
    // even though we already did it when first created a reference
    referredTestEntityContributor.assertNumberOfCalls(1)
  }

  @Test
  fun `reference symbolic id with two reference holders`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)

    model.update("Add reference holder") {
      it addEntity OneMoreWithReferenceTestEntity(listOf(DependencyItem(referredTestEntity.symbolicId)),
                                                  NonPersistentEntitySource)
    }
    // first added for OneMoreWithReferenceTestEntity
    referredTestEntityContributor.assertNumberOfCalls(2)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    model.update("Add one more reference holder") {
      it addEntity OneMoreWithReferenceTestEntity(listOf(DependencyItem(referredTestEntity.symbolicId)),
                                                  NonPersistentEntitySource)
    }
    // second added for OneMoreWithReferenceTestEntity
    referredTestEntityContributor.assertNumberOfCalls(0)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    model.update("Add another type reference holder") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    // first added for WithReferenceTestEntity, 0 because symbolicId was added earlier
    referredTestEntityContributor.assertNumberOfCalls(0)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    model.update("Remove first type first reference holder") {
      it.removeEntity(it.entities(OneMoreWithReferenceTestEntity::class.java).first())
    }
    // first removed OneMoreWithReferenceTestEntity
    referredTestEntityContributor.assertNumberOfCalls(0)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    model.update("Add another type reference holder second reference") {
      it.modifyEntity(WithReferenceTestEntityBuilder::class.java, referenceTestEntity) {
        references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
      }
    }
    // second added for WithReferenceTestEntity
    referredTestEntityContributor.assertNumberOfCalls(0)
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    model.update("Remove first type last reference holder") {
      it.removeEntity(it.entities(OneMoreWithReferenceTestEntity::class.java).first())
    }
    // last removed for OneMoreWithReferenceTestEntity, 0 because it is still in another entity (WithReferenceTestEntity)
    referredTestEntityContributor.assertNumberOfCalls(0)
    // should be in workspace because it is referenced by WithReferenceTestEntity
    readAction {
      assertTrue(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }

    model.update("Remove last reference another holder") {
      it.removeEntity(it.entities(WithReferenceTestEntity::class.java).first())
    }
    // last removed for WithReferenceTestEntity
    referredTestEntityContributor.assertNumberOfCalls(2)
    // the last reference is removed
    readAction {
      assertFalse(WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(entityRoot))
    }
  }

  private fun ReferredTestEntityContributor.assertNumberOfCalls(expected: Int) {
    assertEquals(expected, numberOfCalls.get())
    numberOfCalls.set(0)
  }

  // we need SkipAddingToWatchedRoots to pass filter WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors()
  private class ReferredTestEntityContributor : WorkspaceFileIndexContributor<ReferredTestEntity>, SkipAddingToWatchedRoots {
    val numberOfCalls = AtomicInteger(0)

    override val entityClass: Class<ReferredTestEntity>
      get() = ReferredTestEntity::class.java

    override val dependenciesOnOtherEntities: List<DependencyDescription<ReferredTestEntity>>
      get() = listOf(
        DependencyDescription.OnReference(ReferredTestEntityId::class.java),
      )

    override fun registerFileSets(entity: ReferredTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      numberOfCalls.incrementAndGet()
      if (storage.hasReferrers(entity.symbolicId)) {
        registrar.registerFileSet(entity.file, WorkspaceFileKind.CUSTOM, entity, null)
      }
    }
  }
}