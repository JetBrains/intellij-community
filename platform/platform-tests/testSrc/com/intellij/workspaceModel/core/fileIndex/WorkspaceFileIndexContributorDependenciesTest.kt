// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

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
import com.intellij.util.containers.sequenceOfNotNull
import com.intellij.util.indexing.testEntities.ChildTestEntity
import com.intellij.util.indexing.testEntities.ParentTestEntity
import com.intellij.util.indexing.testEntities.SiblingEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import io.kotest.common.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * This test should not be removed if WorkspaceFileIndexContributor.getDependenciesOnOtherEntities is removed
 * Instead, it needs to be fixed, and we need to make sure that WorkspaceFileIndexContributor.registerFileSets is called for parent/child entities
 */
@TestApplication
class WorkspaceFileIndexContributorDependenciesTest {

  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val childWorkspaceFileIndexContributor = ChildWorkspaceFileIndexContributor()
  private val parentWorkspaceFileIndexContributor = ParentWorkspaceFileIndexContributor()

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var customContentFileSetRoot: VirtualFile
  private lateinit var module: Module


  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(childWorkspaceFileIndexContributor, disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(parentWorkspaceFileIndexContributor, disposable)
    customContentFileSetRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    module = projectModel.createModule()
    // initialize index before the test
    runBlocking { readAction { WorkspaceFileIndex.getInstance(projectModel.project).isInWorkspace(customContentFileSetRoot) } }
    // initialize model
    val model = WorkspaceModel.getInstance(projectModel.project)
    val parent = ParentTestEntity("parent property",
                                  customContentFileSetRoot.toVirtualFileUrl(model.getVirtualFileUrlManager()),
                                  NonPersistentEntitySource)
      .also { it.child = ChildTestEntity("child property", NonPersistentEntitySource) }
      .also { it.secondChild = SiblingEntity("sibling property", NonPersistentEntitySource) }

    runBlocking {
      model.update("Add parent") {
        it.addEntity(parent)
      }
    }
  }

  @Test
  fun `child contributor should be called after parent update`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val parentEntity = model.currentSnapshot.entities(ParentTestEntity::class.java).single()

    model.update("Update parent") {
      it.modifyEntity(ParentTestEntity.Builder::class.java, parentEntity) {
        customParentProperty = "new parent value"
      }
    }

    assertEquals("new parent value", childWorkspaceFileIndexContributor.latestParentProperty, "ChildWorkspaceFileIndexContributor should be called")
  }


  @Test
  fun `parent contributor should be called after child update`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val childEntity = model.currentSnapshot.entities(ChildTestEntity::class.java).single()

    model.update("Update child") {
      it.modifyEntity(ChildTestEntity.Builder::class.java, childEntity) {
        customChildProperty = "new child value"
      }
    }

    assertEquals("new child value", parentWorkspaceFileIndexContributor.latestChildProperty, "ParentWorkspaceFileIndexContributor should be called")
  }

  @Test
  fun `child contributor should be called after its relative update`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val siblingEntity = model.currentSnapshot.entities(SiblingEntity::class.java).single()

    model.update("Update sibling") {
      it.modifyEntity(SiblingEntity.Builder::class.java, siblingEntity) {
        customSiblingProperty = "new sibling property value"
      }
    }

    assertEquals("new sibling property value", childWorkspaceFileIndexContributor.latestSiblingProperty, "ChildWorkspaceFileIndexContributor should be called")
  }

  @Test
  fun `child contributor should be called after its relative removed`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)
    val siblingEntity = model.currentSnapshot.entities(SiblingEntity::class.java).single()

    childWorkspaceFileIndexContributor.numberOfCalls.set(0)

    model.update("Remove sibling") {
      it.removeEntity(siblingEntity)
    }

    // call from WorkspaceFileIndexData:
    // first call changed parent
    // second call to remove file sets

    // two calls through ProjectEntityIndexingService - removed and added file sets
    // case with changed parent is not handled through ProjectEntityIndexingService
    assertEquals(4, childWorkspaceFileIndexContributor.numberOfCalls.get(), "ChildWorkspaceFileIndexContributor should be called after relative removed")
  }

  @Test
  fun `child contributor should be called after its relative added`() = runBlocking {
    val model = WorkspaceModel.getInstance(projectModel.project)

    val newParentEntity = ParentTestEntity("new parent property",
                                           customContentFileSetRoot.toVirtualFileUrl(model.getVirtualFileUrlManager()),
                                           NonPersistentEntitySource) {
      child = ChildTestEntity("new child property", NonPersistentEntitySource)
    }

    childWorkspaceFileIndexContributor.numberOfCalls.set(0)

    val newSiblingEntity = SiblingEntity("new sibling property", NonPersistentEntitySource) {
      parent = newParentEntity
    }

    model.update("Add sibling") {
      it.addEntity(newSiblingEntity)
    }

    // one call through WorkspaceFileIndexData and the other through ProjectEntityIndexingService
    assertEquals(3, childWorkspaceFileIndexContributor.numberOfCalls.get(), "ChildWorkspaceFileIndexContributor should be called after relative added")
  }

  // we need SkipAddingToWatchedRoots to pass filter WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors()
  private class ChildWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ChildTestEntity>, SkipAddingToWatchedRoots {
    var latestParentProperty: String? = null
    var latestSiblingProperty: String? = null
    val numberOfCalls = AtomicInteger(0)

    override val entityClass: Class<ChildTestEntity>
      get() = ChildTestEntity::class.java

    override val dependenciesOnOtherEntities: List<DependencyDescription<ChildTestEntity>>
      get() = listOf(
        DependencyDescription.OnParent(ParentTestEntity::class.java) { sequenceOfNotNull(it.child) },
        DependencyDescription.OnEntity(SiblingEntity::class.java) { sequenceOfNotNull(it.parent.child) }
      )

    override fun registerFileSets(entity: ChildTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      numberOfCalls.incrementAndGet()
      latestParentProperty = entity.parent.customParentProperty
      latestSiblingProperty = entity.parent.secondChild?.customSiblingProperty
    }
  }

  // we need SkipAddingToWatchedRoots to pass filter WorkspaceIndexingRootsBuilder.Companion.registerEntitiesFromContributors()
  private class ParentWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<ParentTestEntity>, SkipAddingToWatchedRoots {
    var latestChildProperty: String? = null

    override val entityClass: Class<ParentTestEntity>
      get() = ParentTestEntity::class.java

    override val dependenciesOnOtherEntities: List<DependencyDescription<ParentTestEntity>>
      get() = listOf(
        DependencyDescription.OnChild(ChildTestEntity::class.java) { it.parent }
      )

    override fun registerFileSets(entity: ParentTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      latestChildProperty = entity.child?.customChildProperty
    }
  }
}
