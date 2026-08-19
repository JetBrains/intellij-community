// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.MutableEntityStorageImpl
import com.intellij.platform.workspace.storage.impl.assertConsistency
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.PCDId1
import com.intellij.platform.workspace.storage.testEntities.entities.PCDIdChild
import com.intellij.platform.workspace.storage.testEntities.entities.PCDIdExtension
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildEntity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdChildReferencer
import com.intellij.platform.workspace.storage.testEntities.entities.PcdExtensionChild
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent1Entity
import com.intellij.platform.workspace.storage.testEntities.entities.PcdParent2Entity
import com.intellij.platform.workspace.storage.testEntities.entities.extensionChildren
import com.intellij.platform.workspace.storage.testEntities.entities.modifyPcdParent1Entity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyPcdParent2Entity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals

class EntityWithDependentSymbolicIdsTest {

  private lateinit var builder: MutableEntityStorageImpl

  @BeforeEach
  fun setUp() {
    builder = createEmptyBuilder()
  }

  private fun addTwoParents(): Pair<PcdParent1Entity, PcdParent2Entity> {
    val parent1 = builder addEntity PcdParent1Entity("parentName1", 1, MySource)
    val parent2 = builder addEntity PcdParent2Entity("parentName2", 2, MySource)
    return parent1 to parent2
  }

  private fun attachChild(data: Boolean, parent1: PcdParent1Entity, parent2: PcdParent2Entity): PcdChildEntity {
    return builder addEntity PcdChildEntity(data, MySource) addChild@{
      builder.modifyPcdParent1Entity(parent1) par1@{
        this@addChild.parent1 = this@par1
      }
      builder.modifyPcdParent2Entity(parent2) par2@{
        this@addChild.parent2 = this@par2
      }
    }
  }

  @Test
  fun `add parents and child that uses their id`() {
    val (parent1, parent2) = addTwoParents()

    val parent1Id = parent1.symbolicId
    val parent2Id = parent2.symbolicId
    val childId = PCDIdChild(false, parent1Id, parent2Id)

    builder.assertConsistency()
    val child = attachChild(false, parent1, parent2)
    builder.assertConsistency()
    assertEquals(parent1, parent1Id.resolve(builder))
    assertEquals(parent2, parent2Id.resolve(builder))
    assertEquals(child, childId.resolve(builder))
    builder.removeEntity(parent1)
    builder.assertConsistency()
    Assertions.assertNull(parent1Id.resolve(builder))
    assertEquals(parent2, parent2Id.resolve(builder))
    Assertions.assertNull(childId.resolve(builder))
  }

  @Test
  fun `add parent and child that uses its id with reversed setter`() {
    val (parent1, parent2) = addTwoParents()

    val parent1Id = parent1.symbolicId
    val parent2Id = parent2.symbolicId
    val childId = PCDIdChild(false, parent1Id, parent2Id)

    builder.assertConsistency()
    val child = builder addEntity PcdChildEntity(false, MySource) addChild@{
      builder.modifyPcdParent1Entity(parent1) par1@{
        this@addChild.parent1 = this@par1
      }
      builder.modifyPcdParent2Entity(parent2) par2@{
        this@par2.children += this@addChild // reversed setter
      }
    }
    builder.assertConsistency()
    assertEquals(parent1, parent1Id.resolve(builder))
    assertEquals(parent2, parent2Id.resolve(builder))
    assertEquals(child, childId.resolve(builder))
    builder.removeEntity(parent1)
    builder.assertConsistency()
    Assertions.assertNull(parent1Id.resolve(builder))
    assertEquals(parent2, parent2Id.resolve(builder))
    Assertions.assertNull(childId.resolve(builder))
  }

  @Test
  fun `add child then change parent id`() {
    val (parent1, parent2) = addTwoParents()

    val firstParent1Id = parent1.symbolicId
    val parent2Id = parent2.symbolicId
    val firstChildId = PCDIdChild(false, firstParent1Id, parent2Id)

    builder.assertConsistency()
    val child = attachChild(false, parent1, parent2)
    builder.assertConsistency()
    val newName = "changedName"
    val newParent1 = builder.modifyPcdParent1Entity(parent1) par1@{
      name = newName
    }
    val newChild = newParent1.child
    builder.assertConsistency()
    Assertions.assertNull(firstParent1Id.resolve(builder))
    Assertions.assertNull(firstChildId.resolve(builder))
    val newParent1Id = PCDId1(newName)
    val newChildId = PCDIdChild(false, newParent1Id, parent2Id)
    assertEquals(newParent1, newParent1Id.resolve(builder))
    assertNotNull(newChild)
    assertEquals(newChild, newChildId.resolve(builder))
  }

  @Test
  fun `add child, add its referencer, change parent id`() {
    val (parent1, parent2) = addTwoParents()
    attachChild(false, parent1, parent2)

    val firstParent1Id = parent1.symbolicId
    val parent2Id = parent2.symbolicId
    val firstChildId = PCDIdChild(false, firstParent1Id, parent2Id)
    val referencer = builder addEntity PcdChildReferencer("referencer", firstChildId, MySource)
    val snapshot1 = builder.toSnapshot()
    snapshot1.assertConsistency()
    Assertions.assertNotNull(referencer.relatedChildEntity.resolve(snapshot1))
    val builder2 = snapshot1.toBuilder()
    val newName = "changedName"
    builder2.modifyPcdParent1Entity(parent1) par1@{
      name = newName
    }
    val snapshot2 = builder2.toSnapshot()
    snapshot2.assertConsistency()
    val newReferencer = run {
      val all = snapshot2.entities<PcdChildReferencer>().toList()
      assertEquals(1, all.size)
      all.single()
    }
    val newChildId = PCDIdChild(false, PCDId1(newName), parent2Id)
    assertEquals(newChildId, newReferencer.relatedChildEntity)
    Assertions.assertNotNull(newReferencer.relatedChildEntity.resolve(snapshot2))
  }

  @Test
  fun `extension children with dependent symbolic id`() {
    val initialName = "parent"
    val changedName = "Blaine the Mono"
    val oneF = 1f
    val twoF = 2f

    val parent1 = builder addEntity PcdParent1Entity(initialName, 1, MySource) {
      extensionChildren = listOf(PcdExtensionChild(oneF, MySource))
    }
    val child2 = builder addEntity PcdExtensionChild(twoF, MySource) ext@{
      builder.modifyPcdParent1Entity(parent1) par@{
        this@ext.parent = this@par
      }
    }
    val parentId1 = PCDId1(initialName)
    val firstChildId1 = PCDIdExtension(oneF, parentId1)
    val secondChildId1 = PCDIdExtension(twoF, parentId1)
    val nonExistentChildId = PCDIdExtension(3f, parentId1)
    val snapshot1 = builder.toSnapshot()
    val parentInSnapshot = snapshot1.entities(PcdParent1Entity::class.java).single()
    assertEquals(2, parentInSnapshot.extensionChildren.size)
    assertEquals(parent1, parentId1.resolve(snapshot1))
    Assertions.assertNotNull(firstChildId1.resolve(snapshot1))
    assertEquals(child2, secondChildId1.resolve(snapshot1))
    Assertions.assertNull(nonExistentChildId.resolve(snapshot1))

    val newBuilder = snapshot1.toBuilder()
    val parent2 = newBuilder.modifyPcdParent1Entity(parentInSnapshot) {
      this.name = changedName
    }
    val snapshot2 = newBuilder.toSnapshot()

    val parentId2 = PCDId1(changedName)
    val firstChildId2 = PCDIdExtension(oneF, parentId2)
    val secondChildId2 = PCDIdExtension(twoF, parentId2)
    assertEquals(parent2, parentId2.resolve(snapshot2))
    Assertions.assertNull(parentId1.resolve(snapshot2))
    Assertions.assertNotNull(firstChildId2.resolve(snapshot2))
    Assertions.assertNull(firstChildId1.resolve(snapshot2))
    Assertions.assertNotNull(secondChildId2.resolve(snapshot2))
    Assertions.assertNull(secondChildId1.resolve(snapshot2))
    Assertions.assertNull(nonExistentChildId.resolve(snapshot2))
  }
}