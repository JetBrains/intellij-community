// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.AbstractEntityStorage
import com.intellij.platform.workspace.storage.impl.references.MutableReferenceContainer
import com.intellij.platform.workspace.storage.impl.references.ReferenceContainer
import com.intellij.platform.workspace.storage.testEntities.entities.*
import org.junit.jupiter.api.Test
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