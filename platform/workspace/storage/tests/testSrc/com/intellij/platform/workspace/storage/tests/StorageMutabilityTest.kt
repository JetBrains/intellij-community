// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.references.MutableReferenceContainer
import com.intellij.platform.workspace.storage.impl.references.ReferenceContainer
import com.intellij.platform.workspace.storage.testEntities.entities.AnotherSource
import com.intellij.platform.workspace.storage.testEntities.entities.AttachedEntityList
import com.intellij.platform.workspace.storage.testEntities.entities.ChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MainEntityList
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.OoChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.OoParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ParentMultipleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SelfLinkedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SourceEntity
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentLeafEntity
import com.intellij.platform.workspace.storage.testEntities.entities.TreeMultiparentRootEntity
import com.intellij.platform.workspace.storage.testEntities.entities.child
import com.intellij.platform.workspace.storage.testEntities.entities.children
import com.intellij.platform.workspace.storage.testEntities.entities.modifyParentEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifySelfLinkedEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyTreeMultiparentLeafEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyTreeMultiparentRootEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StorageMutabilityTest {

  @Test
  fun `check refs copying on empty storage`() {
    val emptyBuilder = createEmptyBuilder()
    compareRefsInstances(emptyBuilder, MutableEntityStorage.from(emptyBuilder.toSnapshot()))

    var snapshot = emptyBuilder.toSnapshot()
    var builder = MutableEntityStorage.from(snapshot)
    compareRefsInstances(snapshot, builder)
    for (i in 1..10) {
      snapshot = builder.toSnapshot()
      compareRefsInstances(snapshot, builder)
      builder = MutableEntityStorage.from(snapshot)
      compareRefsInstances(snapshot, builder)
    }
  }

  @Test
  fun `check complex refs modifications`() {
    val emptyBuilder = createEmptyBuilder()
    val emptyBuilderCopy = MutableEntityStorage.from(emptyBuilder.toSnapshot())

    val entity = MainEntityList("123", MySource) {
      this.child = listOf(AttachedEntityList("xyz", MySource))
    }
    emptyBuilder addEntity entity
    compareRefsInstances(emptyBuilderCopy, emptyBuilder,
                         listOf("OneToManyContainer"))

    var snapshot = emptyBuilder.toSnapshot()
    compareRefsInstances(snapshot, emptyBuilder)

    val builder = MutableEntityStorage.from(snapshot)
    builder addEntity SourceEntity("one", MySource)
    compareRefsInstances(snapshot, builder)

    builder addEntity OoParentEntity("aaa", MySource) {
      child = OoChildEntity("bbb", MySource)
    }
    compareRefsInstances(snapshot, builder,
                         listOf("OneToOneContainer"))
    compareRefsInstances(emptyBuilder, builder,
                         listOf("OneToOneContainer"))
    compareRefsInstances(emptyBuilderCopy, builder,
                         listOf("OneToOneContainer", "OneToManyContainer"))

    snapshot = builder.toSnapshot()
    compareRefsInstances(snapshot, builder)
    compareRefsInstances(emptyBuilder, snapshot,
                         listOf("OneToOneContainer"))
    compareRefsInstances(emptyBuilderCopy, snapshot,
                         listOf("OneToOneContainer", "OneToManyContainer"))
  }

  @Test
  fun `add child by modifying parent`() {
    val builder = createEmptyBuilder()

    val parent = builder addEntity ParentEntity("123", MySource)

    val updatedParent = builder.modifyParentEntity(parent) {
      this.child = ChildEntity("child", MySource)
    }

    assertNotNull(updatedParent.child)
    assertNotNull(builder.entities(ChildEntity::class.java).singleOrNull())
  }

  @Test
  fun `add reference between two existing entities`() {
    val builder = createEmptyBuilder()

    val parent = builder addEntity SelfLinkedEntity(MySource)
    val child = builder addEntity SelfLinkedEntity(AnotherSource)

    val updatedParent = builder.modifySelfLinkedEntity(parent) parent@{
      builder.modifySelfLinkedEntity(child) child@{
        this@child.parentEntity = this@parent
      }
    }

    assertEquals(AnotherSource, updatedParent.children.single().entitySource)
    assertEquals(MySource, builder.entities(SelfLinkedEntity::class.java).single { it.entitySource == AnotherSource }.parentEntity!!.entitySource)
  }

  @Test
  fun `create entity builder and add child by plus equals`() {
    val parent = ParentMultipleEntity("data", MySource) {
      this.children += ChildMultipleEntity("data", MySource)
    }

    assertEquals("data", parent.children.single().childData)
  }

  @Test
  fun `add entity with multiple existing parents`() {
    val builder = createEmptyBuilder()

    val root = builder addEntity TreeMultiparentRootEntity("data", MySource)
    val parent = builder addEntity TreeMultiparentLeafEntity("data", MySource)

    val updatedRoot = builder.modifyTreeMultiparentRootEntity(root) root@{
      builder.modifyTreeMultiparentLeafEntity(parent) parent@{
        builder addEntity TreeMultiparentLeafEntity("AnotherData", MySource) child@{
          this@child.mainParent = this@root
          this@child.leafParent = this@parent
        }
      }
    }

    assertEquals("AnotherData", updatedRoot.children.single().data)

    val foundEntity = builder.entities(TreeMultiparentLeafEntity::class.java).single { it.data == "AnotherData" }
    assertNotNull(foundEntity.mainParent)
    assertNotNull(foundEntity.leafParent)
  }

  @Test
  fun `modification on second level`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity ParentEntity("123", MySource) {
      this.child = ChildEntity("child", MySource)
    }

    val secondBuilder = builder.toSnapshot().toBuilder()

    assertThrows<IllegalStateException> {
      secondBuilder.modifyParentEntity(parent) {
        this.child!!.childData = "NEW_DATA"
      }
    }
  }

  private fun compareRefsInstances(originalStorage: EntityStorage, copiedStorage: EntityStorage,
                                   differentContainers: List<String> = listOf()) {
    originalStorage as AbstractEntityStorage
    copiedStorage as AbstractEntityStorage

    assertNotSame(originalStorage, copiedStorage)

    val originalRefs = originalStorage.refs
    val copiedRefs = copiedStorage.refs
    assertNotSame(originalRefs, copiedRefs)

    if (originalRefs.oneToManyContainer.nameInCollection(differentContainers)) {
      compareNotSameRefsContainer(originalRefs.oneToManyContainer, copiedRefs.oneToManyContainer)
    } else {
      compareSameRefsContainer(originalRefs.oneToManyContainer, copiedRefs.oneToManyContainer)
    }
    if (originalRefs.oneToOneContainer.nameInCollection(differentContainers)) {
      compareNotSameRefsContainer(originalRefs.oneToOneContainer, copiedRefs.oneToOneContainer)
    } else {
      compareSameRefsContainer(originalRefs.oneToOneContainer, copiedRefs.oneToOneContainer)
    }
    if (originalRefs.abstractOneToOneContainer.nameInCollection(differentContainers)) {
      compareNotSameRefsContainer(originalRefs.abstractOneToOneContainer, copiedRefs.abstractOneToOneContainer)
    } else {
      compareSameRefsContainer(originalRefs.abstractOneToOneContainer, copiedRefs.abstractOneToOneContainer)
    }
    if (originalRefs.oneToAbstractManyContainer.nameInCollection(differentContainers)) {
      compareNotSameRefsContainer(originalRefs.oneToAbstractManyContainer, copiedRefs.oneToAbstractManyContainer)
    } else {
      compareSameRefsContainer(originalRefs.oneToAbstractManyContainer, copiedRefs.oneToAbstractManyContainer)
    }
  }

  private fun compareNotSameRefsContainer(originalContainer: ReferenceContainer<*>, copiedContainer: ReferenceContainer<*>) {
    assertNotSame(originalContainer, copiedContainer)
    assertNotSame(originalContainer.getInternalStructure(), copiedContainer.getInternalStructure())
  }

  private fun compareSameRefsContainer(originalContainer: ReferenceContainer<*>, copiedContainer: ReferenceContainer<*>) {
    assertNotSame(originalContainer, copiedContainer)
    assertSame(originalContainer.getInternalStructure(), copiedContainer.getInternalStructure())
    if (originalContainer is MutableReferenceContainer && copiedContainer !is MutableReferenceContainer) {
      assertTrue(originalContainer.isFreezed())
    } else if (originalContainer !is MutableReferenceContainer && copiedContainer is MutableReferenceContainer) {
      assertTrue(copiedContainer.isFreezed())
    }
  }

  private fun ReferenceContainer<*>.nameInCollection(collection: List<String>): Boolean {
    val simpleName = this::class.simpleName
    return collection.find { simpleName == "Mutable$it" || simpleName == "Immutable$it" } != null
  }
}