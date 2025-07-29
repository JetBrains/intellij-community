// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.SkipAddingToWatchedRoots
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.indexing.testEntities.DependencyItem
import com.intellij.util.indexing.testEntities.WithReferenceTestEntity
import com.intellij.util.indexing.testEntities.ReferredTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import io.kotest.common.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

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

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(referredTestEntityContributor, disposable)
    customContentFileSetRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    module = projectModel.createModule()
    // initialize index before the test
    runBlocking { readAction { WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(customContentFileSetRoot) } }
    // initialize model
    val model = WorkspaceModel.getInstance(projectModel.project)
    val referenceTestEntity = WithReferenceTestEntity("ReferenceTestEntity",
                                                  emptyList(),
                                                  NonPersistentEntitySource)

    val referredTestEntity = ReferredTestEntity("ReferredTestEntity", NonPersistentEntitySource)
    runBlocking {
      model.update("Add reference and referred entity without direct reference") {
        it.addEntity(referenceTestEntity)
        it.addEntity(referredTestEntity)
      }
    }
  }


  @Test
  fun `check referred test entity contributor called after reference is created`() {
    val referenceTestEntity = WorkspaceModel.getInstance(projectModel.project).currentSnapshot.entities(WithReferenceTestEntity::class.java).single()
    val referredTestEntity = WorkspaceModel.getInstance(projectModel.project).currentSnapshot.entities(ReferredTestEntity::class.java).single()

    referredTestEntityContributor.numberOfCalls.set(0)
    runBlocking {
      WorkspaceModel.getInstance(projectModel.project).update("Create reference between entities") {
        it.modifyEntity(WithReferenceTestEntity.Builder::class.java, referenceTestEntity) {
          references = mutableListOf(DependencyItem(referredTestEntity.symbolicId))
        }
      }
    }

    assertEquals(referredTestEntityContributor.numberOfCalls.get(), 1)
  }


  @Test
  fun `check referred test entity contributor called after reference is removed`() {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val referenceTestEntity = model.currentSnapshot.entities(WithReferenceTestEntity::class.java).single()
    val referredTestEntity = model.currentSnapshot.entities(ReferredTestEntity::class.java).single()

    runBlocking {
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
    }

    assertEquals(referredTestEntityContributor.numberOfCalls.get(), 1)
  }

  // we need SkipAddingToWatchedRoots to pass filter WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors()
  private class ReferredTestEntityContributor : WorkspaceFileIndexContributor<ReferredTestEntity>, SkipAddingToWatchedRoots {
    val numberOfCalls = AtomicInteger(0)

    override val entityClass: Class<ReferredTestEntity>
      get() = ReferredTestEntity::class.java

    override val dependenciesOnOtherEntities: List<DependencyDescription<ReferredTestEntity>>
      get() = listOf(
        DependencyDescription.OnReference(WithReferenceTestEntity::class.java, ReferredTestEntity::class.java) {
          it.references.asSequence().map { it.reference }
        },
      )

    override fun registerFileSets(entity: ReferredTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      numberOfCalls.incrementAndGet()
    }
  }
}