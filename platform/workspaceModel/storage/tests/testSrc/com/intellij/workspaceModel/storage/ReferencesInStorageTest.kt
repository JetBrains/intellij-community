// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.assertConsistency
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun EntityStorage.singleParent() = entities(XParentEntity::class.java).single()

private fun EntityStorage.singleChild() = entities(XChildEntity::class.java).single()

class ReferencesInStorageTest {
  private lateinit var virtualFileManager: VirtualFileUrlManager

  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `add entity`() {
    val builder = createEmptyBuilder()
    val child = XChildEntity("child", MySource) {
      dataClass = null
      parentEntity = XParentEntity("foo", MySource) {
        this.optionalChildren = emptyList()
        this.childChild = emptyList()
      }
      this.childChild = emptyList()
    }
    builder.addEntity(child)
    builder.assertConsistency()
    assertEquals("foo", child.parentEntity.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parentEntity, builder.singleParent())
    assertEquals(child, child.parentEntity.children.single())
  }

  @Test
  fun `add entity via diff`() {
    val builder = createEmptyBuilder()
    val parentEntity = XParentEntity("foo", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    builder.addEntity(parentEntity)

    val diff = createBuilderFrom(builder.toSnapshot())
    val childEntity = XChildEntity("child", MySource) {
      dataClass = null
      this.parentEntity = parentEntity
      this.childChild = emptyList()
    }
    diff.addEntity(childEntity)
    builder.addDiff(diff)
    builder.assertConsistency()

    val child = builder.singleChild()
    assertEquals("foo", child.parentEntity.parentProperty)
    assertEquals(child, builder.singleChild())
    assertEquals(child.parentEntity, builder.singleParent())
    assertEquals(child, child.parentEntity.children.single())
  }

  @Test
  fun `add remove reference inside data class`() {
    val builder = createEmptyBuilder()
    val parent1 = XParentEntity("parent1", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    val parent2 = XParentEntity("parent2", MySource) {
      children = emptyList()
      this.optionalChildren = emptyList()
      this.childChild = emptyList()
    }
    builder.addEntity(parent1)
    builder.addEntity(parent2)
    builder.assertConsistency()
    val child = XChildEntity("child", MySource) {
      dataClass = DataClassX("data", builder.createReference(parent2))
      this.parentEntity = parent1
      this.childChild = emptyList()
    }
    builder.addEntity(child)
    builder.assertConsistency()
    assertEquals(child, parent1.children.single())
    assertEquals(emptyList<XChildEntity>(), parent2.children.toList())
    assertEquals("parent1", child.parentEntity.parentProperty)
    assertEquals("parent2", child.dataClass!!.parent.resolve(builder)?.parentProperty)
    assertEquals(setOf(parent1, parent2), builder.entities(XParentEntity::class.java).toSet())

    builder.modifyEntity(child) {
      dataClass = null
    }
    builder.assertConsistency()
    assertEquals(setOf(parent1, parent2), builder.entities(XParentEntity::class.java).toSet())
  }

  @Test
  fun `remove child entity`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity XParentEntity("parent", MySource)
    builder.assertConsistency()
    val child = builder addEntity XChildEntity("child", MySource) {
      parentEntity = parent
    }
    builder.assertConsistency()
    builder.removeEntity(child)
    builder.assertConsistency()
    assertEquals(emptyList<XChildEntity>(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList<XChildEntity>(), parent.children.toList())
    assertEquals(parent, builder.singleParent())
  }

  @Test
  fun `remove parent entity`() {
    val builder = createEmptyBuilder()
    val child = builder addEntity XChildEntity("child", MySource) {
      parentEntity = builder addEntity XParentEntity("parent", MySource)
    }
    builder.removeEntity(child.parentEntity)
    builder.assertConsistency()
    assertEquals(emptyList<XChildEntity>(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList<XParentEntity>(), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity via diff`() {
    val builder = createEmptyBuilder()
    val oldParent = builder addEntity XParentEntity("oldParent", MySource)
    val oldChild = builder addEntity XChildEntity("oldChild", MySource) {
      parentEntity = oldParent
    }
    val diff = createEmptyBuilder()
    val parent = diff addEntity XParentEntity("newParent", MySource)
    diff addEntity XChildEntity("newChild", MySource) {
      parentEntity = parent
    }
    diff.removeEntity(parent)
    diff.assertConsistency()
    builder.addDiff(diff)
    builder.assertConsistency()
    assertEquals(listOf(oldChild), builder.entities(XChildEntity::class.java).toList())
    assertEquals(listOf(oldParent), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity with two children`() {
    val builder = createEmptyBuilder()
    val child1 = builder addEntity XChildEntity("child", MySource) {
      parentEntity = builder addEntity XParentEntity("parent", MySource)
      dataClass = null
      childChild = emptyList<XChildChildEntity>()
    }
    builder addEntity XChildEntity("child", MySource) {
      parentEntity = child1.parentEntity
    }
    builder.removeEntity(child1.parentEntity)
    builder.assertConsistency()
    assertEquals(emptyList<XChildEntity>(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList<XParentEntity>(), builder.entities(XParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity in DAG`() {
    val builder = createEmptyBuilder()
    val parent = builder addEntity XParentEntity("parent", MySource)
    val child = builder addEntity XChildEntity("child", MySource) {
      parentEntity = parent
    }
    builder addEntity XChildChildEntity(MySource) {
      parent1 = parent
      parent2 = child
    }
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<XChildEntity>(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList<XParentEntity>(), builder.entities(XParentEntity::class.java).toList())
  }

  // UNSUPPORTED
  /*
  @Test
  fun `remove parent entity referenced via data class`() {
    val builder = PEntityStorageBuilder.create()
    val parent1 = builder.addPParentEntity("parent1")
    val parent2 = builder.addPParentEntity("parent2")
    builder.addPChildEntity(parent1, "child", PDataClass("data", builder.createReference(parent2)))
    builder.removeEntity(parent2)
    builder.assertConsistency()
    assertEquals(emptyList<PChildEntity>(), builder.entities(PChildEntity::class.java).toList())
    assertEquals(listOf(parent1), builder.entities(PParentEntity::class.java).toList())
    assertEquals(emptyList<PChildEntity>(), parent1.children.toList())
  }
*//*


  @Test
  fun `remove parent entity referenced via two paths`() {
    val builder = createEmptyBuilder()
    val parent = builder.addParentEntity()
    builder.addChildEntity(parent, "child", DataClass("data", builder.createReference(parent)))
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<XChildEntity>(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `remove parent entity referenced via two paths via entity ref`() {
    val builder = createEmptyBuilder()
    val parent = builder.addParentEntity()
    builder.addChildEntity(parent, "child", DataClassX("data", parent.createReference()))
    builder.assertConsistency()
    builder.removeEntity(parent)
    builder.assertConsistency()
    assertEquals(emptyList<XChildEntity>(), builder.entities(XChildEntity::class.java).toList())
    assertEquals(emptyList<ParentEntity>(), builder.entities(ParentEntity::class.java).toList())
  }

  @Test
  fun `modify parent property`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity
    val newParent = builder.modifyEntity(child.parentEntity) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parentEntity)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify parent property via diff`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity

    val diff = createBuilderFrom(builder)
    diff.modifyEntity(ModifiableParentEntity::class.java, child.parentEntity) {
      parentProperty = "changed"
    }
    builder.addDiff(diff)
    builder.assertConsistency()
    val newParent = builder.singleParent()
    assertEquals("changed", newParent.parentProperty)
    assertEquals(newParent, builder.singleParent())
    assertEquals(newParent, child.parentEntity)
    assertEquals(child, newParent.children.single())
    assertEquals("parent", oldParent.parentProperty)
  }

  @Test
  fun `modify child property`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity
    val newChild = builder.modifyEntity(child) {
      childProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", newChild.childProperty)
    assertEquals(oldParent, builder.singleParent())
    assertEquals(newChild, builder.singleChild())
    assertEquals(oldParent, newChild.parent)
    assertEquals(oldParent, child.parentEntity)
    assertEquals(newChild, oldParent.children.single())
    assertEquals("child", child.childProperty)
  }

  @Test
  fun `modify reference to parent`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())
    val oldParent = child.parentEntity
    val newParent = builder.addParentEntity("new")
    val newChild = builder.modifyEntity(child) {
      parentEntity = newParent
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals(setOf(oldParent, newParent), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.parent)
    assertEquals(newChild, newParent.children.single())

    assertEquals(newParent, child.parentEntity)
    //assertEquals(oldParent, child.parentEntity)  // ProxyBasedStore behaviour

    assertEquals(emptyList<XChildEntity>(), oldParent.children.toList())
  }

  @Test
  fun `modify reference to parent via data class`() {
    val builder = createEmptyBuilder()
    val parent1 = builder.addParentEntity("parent1")
    val oldParent = builder.addParentEntity("parent2")
    val child = builder.addChildEntity(parent1, "child", DataClassX("data", builder.createReference(oldParent)))
    val newParent = builder.addParentEntity("new")
    builder.assertConsistency()
    val newChild = builder.modifyEntity(child) {
      dataClass = DataClass("data2", builder.createReference(newParent))
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.dataClass.parent.resolve(builder))
    assertEquals(oldParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `modify reference to parent via data class via entity ref`() {
    val builder = createEmptyBuilder()
    val parent1 = builder.addParentEntity("parent1")
    val oldParent = builder.addParentEntity("parent2")
    val child = builder.addChildEntity(parent1, "child", DataClass("data", oldParent.createReference()))
    val newParent = builder.addParentEntity("new")
    builder.assertConsistency()
    val newChild = builder.modifyEntity(child) {
      dataClass = DataClass("data2", newParent.createReference())
    }
    builder.assertConsistency()
    assertEquals("child", newChild.childProperty)
    assertEquals("data2", newChild.dataClass!!.stringProperty)
    assertEquals(setOf(oldParent, newParent, parent1), builder.entities(ParentEntity::class.java).toSet())
    assertEquals(newChild, builder.singleChild())
    assertEquals(newParent, newChild.dataClass.parent.resolve(builder))
    assertEquals(oldParent, child.dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `builder from storage`() {
    val storage = createEmptyBuilder().apply {
      addChildEntity(addParentEntity())
    }.toStorage()
    storage.assertConsistency()

    assertEquals("parent", storage.singleParent().parentProperty)

    val builder = createBuilderFrom(storage)
    builder.assertConsistency()

    val oldParent = builder.singleParent()
    assertEquals("parent", oldParent.parentProperty)
    val newParent = builder.modifyEntity(ModifiableParentEntity::class.java, oldParent) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(newParent, builder.singleChild().parent)
    assertEquals("changed", builder.singleChild().parent.parentProperty)
    assertEquals("parent", storage.singleChild().parent.parentProperty)

    val parent2 = builder.addParentEntity("parent2")
    builder.modifyEntity(builder.singleChild()) {
      dataClass = DataClass("data", builder.createReference(parent2))
    }
    builder.assertConsistency()
    assertEquals("parent", storage.singleParent().parentProperty)
    assertEquals(null, storage.singleChild().dataClass)
    assertEquals("data", builder.singleChild().dataClass!!.stringProperty)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
    assertEquals(setOf(parent2, newParent), builder.entities(ParentEntity::class.java).toSet())
  }

  @Test
  fun `storage from builder`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildEntity(builder.addParentEntity())

    val snapshot = builder.toStorage()
    builder.assertConsistency()

    builder.modifyEntity(ModifiableParentEntity::class.java, child.parentEntity) {
      parentProperty = "changed"
    }
    builder.assertConsistency()
    assertEquals("changed", builder.singleParent().parentProperty)
    assertEquals("changed", builder.singleChild().parent.parentProperty)
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals("parent", snapshot.singleChild().parent.parentProperty)

    val parent2 = builder.addParentEntity("new")
    builder.modifyEntity(child) {
      dataClass = DataClassX("data", builder.createReference(parent2))
    }
    builder.assertConsistency()
    assertEquals("parent", snapshot.singleParent().parentProperty)
    assertEquals(null, snapshot.singleChild().dataClass)
    assertEquals(parent2, builder.singleChild().dataClass!!.parent.resolve(builder))
  }

  @Test
  fun `modify optional parent property`() {
    val builder = createEmptyBuilder()
    val child = builder.addChildWithOptionalParentEntity(null)
    assertNull(child.optionalParent)
    val newParent = builder.addParentEntity()
    assertEquals(emptyList<ChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
    val newChild = builder.modifyEntity(child) {
      optionalParent = newParent
    }
    builder.assertConsistency()
    assertEquals(newParent, newChild.optionalParent)
    assertEquals(newChild, newParent.optionalChildren.single())

    val veryNewChild = builder.modifyEntity(newChild) {
      optionalParent = null
    }
    assertNull(veryNewChild.optionalParent)
    assertEquals(emptyList<ChildWithOptionalParentEntity>(), newParent.optionalChildren.toList())
  }

  @Test
  fun `removing one to one parent`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)

    builder.removeEntity(parentEntity)

    val parents = builder.entities(OoParentEntity::class.java).toList()
    val children = builder.entities(OoChildEntity::class.java).toList()

    assertEmpty(parents)
    assertEmpty(children)
  }

  @Test
  fun `add one to one entities`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)

    builder.assertConsistency()
  }

  @Test
  fun `test replace by source one to one nullable ref with parent persistent Id and child without it`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()

    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  @Ignore("Skip because of unstable rbs")
  fun `test replace by source one to one ref with parent persistent Id and child with persistent Id`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "childOne")
    builder.addOoChildAlsoWithPidEntity(parentEntity, "childTwo")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "childOneOne", source = AnotherSource)
    newBuilder.addOoChildAlsoWithPidEntity(parentEntity, "childTwo", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()

    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val firstChild = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    val secondChild = builder.entities(OoChildAlsoWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, firstChild.entitySource)
    assertEquals(AnotherSource, secondChild.entitySource)
    assertEquals("childOneOne", firstChild.childProperty)
    assertEquals(parent, firstChild.parent)
    assertEquals("childTwo", secondChild.childProperty)
    assertEquals(parent, secondChild.parent)
  }

  @Test
  fun `test replace by source with parent persistent Id and without children`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()
    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(MySource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  fun `test replace by source one to one ref with parent persistent Id and child without it and parent entity source intersection`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent", AnotherSource)
    builder.addOoChildForParentWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()
    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  fun `test replace by source one to one ref with parent persistent Id and child without it and child entity source intersection`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithPidEntity("parent")
    builder.addOoChildForParentWithPidEntity(parentEntity, "child", AnotherSource)
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithPidEntity("parent", AnotherSource)
    newBuilder.addOoChildForParentWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()
    val parent = builder.entities(OoParentWithPidEntity::class.java).single()
    val child = builder.entities(OoChildForParentWithPidEntity::class.java).single()
    assertEquals(AnotherSource, parent.entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals(child, parent.childOne)
  }

  @Test
  fun `test replace by source one to one nullable ref with child persistent Id and parent without it`() {
    val builder = createEmptyBuilder()
    var parentEntity = builder.addOoParentWithoutPidEntity("parent")
    builder.addOoChildWithPidEntity(parentEntity, "child")
    builder.checkConsistency()

    val newBuilder = createEmptyBuilder()
    parentEntity = newBuilder.addOoParentWithoutPidEntity("parent", AnotherSource)
    newBuilder.addOoChildWithPidEntity(parentEntity, "child", source = AnotherSource)
    newBuilder.checkConsistency()

    builder.replaceBySource({ it is AnotherSource }, newBuilder)
    builder.checkConsistency()

    val listOfParent = builder.entities(OoParentWithoutPidEntity::class.java).toList()
    val child = builder.entities(OoChildWithPidEntity::class.java).single()
    assertEquals(2, listOfParent.size)
    assertEquals(MySource, listOfParent[0].entitySource)
    assertEquals(AnotherSource, listOfParent[1].entitySource)
    assertEquals(AnotherSource, child.entitySource)
    assertEquals("child", child.childProperty)
    assertEquals(listOfParent[1], child.parentEntity)
  }

  @Test
  fun `add child to a single parent`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)
    builder.addOoChildEntity(parentEntity)

    builder.assertConsistency()

    // Child has a not-null parent, so we remove it one field replace
    val children = builder.entities(OoChildEntity::class.java).toList()
    assertEquals(1, children.size)
  }

  @Test
  fun `double child adding`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildWithNullableParentEntity(parentEntity)
    builder.addOoChildWithNullableParentEntity(parentEntity)

    builder.assertConsistency()

    // Child has a nullable parent. So we just unlink a parent from one of the entities
    val children = builder.entities(OoChildWithNullableParentEntity::class.java).toList()
    assertEquals(2, children.size)

    if (children[0].parent == null) {
      assertNotNull(children[1].parent)
    }
    else {
      assertNull(children[1].parent)
    }
  }

  @Test
  fun `remove children`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addParentEntity()
    val childEntity = builder.addChildEntity(parentEntity)

    builder.changeLog.clear()

    builder.modifyEntity(ModifiableParentEntity::class.java, parentEntity) {
      this.children = emptyList<XChildEntity>().asSequence()
    }

    builder.assertConsistency()

    assertTrue(builder.entities(XChildEntity::class.java).toList().isEmpty())

    assertInstanceOf(builder.changeLog.changeLog[childEntity.id], ChangeEntry.RemoveEntity::class.java)
  }

  @Test
  fun `remove multiple children`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addParentEntity()
    val childEntity1 = builder.addChildEntity(parentEntity)
    val childEntity2 = builder.addChildEntity(parentEntity)

    builder.changeLog.clear()

    builder.modifyEntity(ModifiableParentEntity::class.java, parentEntity) {
      this.children = sequenceOf(childEntity2)
    }

    builder.assertConsistency()

    assertEquals(1, builder.entities(XChildEntity::class.java).toList().size)

    assertInstanceOf(builder.changeLog.changeLog[childEntity1.id], ChangeEntry.RemoveEntity::class.java)
  }

  @Test
  fun `check store consistency after deserialization`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addParentEntity()
    builder.addChildEntity(parentEntity)
    builder.addChildEntity(parentEntity)
    builder.assertConsistency()

    builder.removeEntity(parentEntity.id)
    builder.assertConsistency()

    val stream = ByteArrayOutputStream()
    val serializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
    serializer.serializeCache(stream, builder.toStorage())
    val byteArray = stream.toByteArray()

    // Deserialization won't create collection which consists only from null elements
    val deserializer = EntityStorageSerializerImpl(TestEntityTypesResolver(), VirtualFileUrlManagerImpl())
    val deserialized = (deserializer.deserializeCache(ByteArrayInputStream(byteArray)) as? WorkspaceEntityStorageBuilderImpl)?.toStorage()
    deserialized!!.assertConsistency()
  }

  @Test
  fun `replace one to one connection with adding already existing child`() {
    val builder = createEmptyBuilder()
    val parentEntity = builder.addOoParentWithPidEntity()
    builder.addOoChildForParentWithPidEntity(parentEntity)

    val anotherBuilder = createBuilderFrom(builder)
    anotherBuilder.addOoChildForParentWithPidEntity(parentEntity, childProperty = "MyProperty")

    // Modify initial builder
    builder.addOoChildForParentWithPidEntity(parentEntity)

    builder.addDiff(anotherBuilder)

    builder.assertConsistency()

    assertEquals("MyProperty", builder.entities(OoParentWithPidEntity::class.java).single().childOne!!.childProperty)
  }

  @Test
  fun `pull one to one connection into another builder`() {
    val builder = createEmptyBuilder()

    val anotherBuilder = createBuilderFrom(builder)
    val parentEntity = anotherBuilder.addOoParentEntity()
    anotherBuilder.addOoChildWithNullableParentEntity(parentEntity)

    builder.addDiff(anotherBuilder)

    builder.assertConsistency()

    UsefulTestCase.assertNotEmpty(builder.entities(OoParentEntity::class.java).toList())
    UsefulTestCase.assertNotEmpty(builder.entities(OoChildWithNullableParentEntity::class.java).toList())
  }
*/
}
