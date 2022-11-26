package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.ChildEntity
import com.intellij.workspaceModel.storage.entities.test.api.MySource
import com.intellij.workspaceModel.storage.entities.test.api.ParentEntity
import com.intellij.workspaceModel.storage.entities.test.api.modifyEntity
import com.intellij.workspaceModel.storage.impl.EntityStorageSnapshotImpl
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * References between entities of different storages
 * - Creation of an entity with a reference to an "added-to-storage" entity is allowed
 * - While adding parent entity to the store, store verifies of the child exists in this storage
 *   - Should we replace child diff reference?
 * - Existing verification happens by EntityId and equality (we still should make EntityId reliable)
 *
 * So, the operation will succeed in cases:
 * - builder2 made from builder1 Parent is added to builder2 that is made from builder1 if the child is originally created in builder1
 * Operation won't succeed:
 * - builder1 and builder2 are independent. If we create a child in builder1 and try to add a parent in builder2
 *     the operation will fail.
 *   But how can we transfer builder1 into builder2? Even if we'll add builde1 into builde2
 *     using addDiff or something, this would mean EntityId change and the verification will fail. At the moment this
 *     is not intuitive.
 */

class ParentAndChildTest {
  @Test
  fun `parent with child`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    assertNotNull(entity.child)
    assertEquals("ChildData", entity!!.child!!.childData)
  }

  @Test
  fun `parent with child in builder`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ParentEntity::class.java).single()
    assertEquals("ChildData", single.child!!.childData)
  }

  @Test
  fun `parent with child in builder 2`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val snapshot = builder.toSnapshot()
    val parentSnapshot = snapshot.entities(ParentEntity::class.java).single()

    val builder1 = snapshot.toBuilder()
    builder1.removeEntity(parentSnapshot)
    builder1.removeEntity(parentSnapshot!!.child!!)

    val newSnapshot = builder1.toSnapshot()
    (newSnapshot as EntityStorageSnapshotImpl).assertConsistency()
    assertTrue(newSnapshot.entities(ParentEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `remove entity twice`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val snapshot = builder.toSnapshot()
    val parentSnapshot = snapshot.entities(ParentEntity::class.java).single()

    val builder1 = snapshot.toBuilder()
    builder1.removeEntity(parentSnapshot)
    builder1.removeEntity(parentSnapshot)

    val newSnapshot = builder1.toSnapshot()
    (newSnapshot as EntityStorageSnapshotImpl).assertConsistency()
    assertTrue(newSnapshot.entities(ParentEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `get parent from child`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    val single = builder.entities(ChildEntity::class.java).single()
    assertEquals("ChildData", single.parentEntity.child!!.childData)
  }

  @Test
  fun `parent with child in builder and accessing original`() {
    val entity = ParentEntity("ParentData", MySource) {
      child = ChildEntity("ChildData", MySource)
    }

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertEquals("ChildData", entity.child!!.childData)
  }

  @Test
  fun `changed properties is empty after adding to storage`() {
    val entity = ParentEntity("ParentData", MySource)

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    assertTrue((entity as ModifiableWorkspaceEntityBase<*, *>).changedProperty.isEmpty())
  }

  @Test
  fun `changed properties is empty after adding to storage 2`() {
    val entity = ParentEntity("ParentData", MySource)

    val builder = MutableEntityStorage.create()
    builder.addEntity(entity)

    builder.modifyEntity(entity) {
      this.parentData = "NewData"
    }

    assertEquals("NewData", entity.parentData)
    assertEquals("NewData", builder.entities(ParentEntity::class.java).single().parentData)
  }
}