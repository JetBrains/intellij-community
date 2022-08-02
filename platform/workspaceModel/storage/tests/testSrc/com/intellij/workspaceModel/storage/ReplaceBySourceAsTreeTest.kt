// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspaceModel.storage.entities.test.api.*
import com.intellij.workspaceModel.storage.impl.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertNotNull


/**
 * Replace by source test plan
 *
 * Plus sign means implemented
 *
 *   + Same entity modification
 *   + Add entity in remote builder
 *   + Remove entity in remote builder
 *   + Parent + child - modify parent
 *   + Parent + child - modify child
 *   + Parent + child - remove child
 *   + Parent + child - remove parent
 * - Parent + child - wrong source for parent nullable
 * - Parent + child - wrong source for parent not null
 * - Parent + child - wrong source for child nullable
 * - Parent + child - wrong source for child not null
 *   + Parent + child - wrong source for parent in remote builder
 *   + Parent + child - wrong source for child in remote builder
 *
 * Soft links
 *   + Change property of persistent id
 * - Change property of persistent id. Entity with link should be in wrong source
 * - Change property of persistent id. Entity with persistent id should be in wrong source (what should happen?)
 *
 *
 * different connection types
 * persistent id
 */
class ReplaceBySourceAsTreeTest {

  private lateinit var builder: MutableEntityStorageImpl
  private lateinit var replacement: MutableEntityStorageImpl

  @Before
  fun setUp() {
    builder = createEmptyBuilder()
    replacement = createEmptyBuilder()
    builder.useNewRbs = true
    builder.keepLastRbsEngine = true
  }

  @Test
  fun `add entity`() {
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    replacement = createEmptyBuilder()
    replacement add NamedEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({ it == SampleEntitySource("1") }, replacement)
    assertEquals(setOf("hello1", "hello2"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    builder.assertConsistency()
  }

  @Test
  fun `remove entity`() {
    val source1 = SampleEntitySource("1")
    builder add NamedEntity("hello1", source1)
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    builder.replaceBySource({ it == source1 }, createEmptyBuilder())
    assertEquals("hello2", builder.entities(NamedEntity::class.java).single().myName)
    builder.assertConsistency()
  }

  @Test
  fun `remove and add entity`() {
    val source1 = SampleEntitySource("1")
    builder add NamedEntity("hello1", source1)
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    replacement = createEmptyBuilder()
    replacement add NamedEntity("updated", source1)
    builder.replaceBySource({ it == source1 }, replacement)
    assertEquals(setOf("hello2", "updated"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    builder.assertConsistency()
  }

  @Test
  fun `multiple sources`() {
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val sourceB = SampleEntitySource("b")
    val parent1 = builder add NamedEntity("a", sourceA1)
    builder add NamedEntity("b", sourceB)
    replacement = createEmptyBuilder()
    val parent3 = replacement add NamedEntity("new", sourceA2)
    builder.replaceBySource({ it is SampleEntitySource && it.name.startsWith("a") }, replacement)
    assertEquals(setOf("b", "new"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    builder.assertConsistency()
    thisStateCheck {
      parent1 assert ReplaceState.Remove
    }
    replaceWithCheck { parent3 assert ReplaceWithState.SubtreeMoved }
  }

  @Test
  fun `work with different entity sources`() {
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val parentEntity = builder add NamedEntity("hello", sourceA1)
    replacement = createBuilderFrom(builder)
    replacement add NamedChildEntity("child", sourceA2) {
      this.parentEntity = parentEntity
    }
    builder.replaceBySource({ it == sourceA2 }, replacement)
    assertEquals(1, builder.toSnapshot().entities(NamedEntity::class.java).toList().size)
    assertEquals(1, builder.toSnapshot().entities(NamedChildEntity::class.java).toList().size)
    assertEquals("child", builder.toSnapshot().entities(NamedEntity::class.java).single().children.single().childProperty)
    builder.assertConsistency()
  }

  @Test
  fun `empty storages`() {
    val builder2 = createEmptyBuilder()

    builder.replaceBySource(trueSources, builder2)
    assertTrue(builder.collectChanges(createEmptyBuilder()).isEmpty())
    builder.assertConsistency()
  }

  @Test
  fun `replace with empty storage`() {
    val parent1 = builder add NamedEntity("data1", MySource)
    val parent2 = builder add NamedEntity("data2", MySource)
    resetChanges()
    val originalStorage = builder.toSnapshot()

    builder.replaceBySource(trueSources, createEmptyBuilder())
    val collectChanges = builder.collectChanges(originalStorage)
    assertEquals(1, collectChanges.size)
    assertEquals(2, collectChanges.values.single().size)
    assertTrue(collectChanges.values.single().all { it is EntityChange.Removed<*> })
    builder.assertConsistency()

    assertNoNamedEntities()
    thisStateCheck {
      parent1 assert ReplaceState.Remove
      parent2 assert ReplaceState.Remove
    }
  }

  @Test
  fun `add entity with false source`() {
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    resetChanges()
    replacement = createEmptyBuilder()
    replacement add NamedEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({ false }, replacement)
    assertEquals(setOf("hello2"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    assertTrue(builder.collectChanges(createEmptyBuilder()).isEmpty())
    builder.assertConsistency()
  }

  @Test
  fun `entity modification`() {
    val entity = builder add NamedEntity("hello2", MySource)
    replacement = createBuilderFrom(builder)
    val modified = replacement.modifyEntity(entity) {
      myName = "Hello Alex"
    }

    rbsAllSources()

    builder.assertConsistency()
    assertSingleNameEntity("Hello Alex")

    thisStateCheck {
      entity assert ReplaceState.Remove
    }

    replaceWithCheck {
      modified assert ReplaceWithState.SubtreeMoved
    }
  }

  @Test
  fun `adding entity in builder`() {
    replacement = createBuilderFrom(builder)
    replacement add NamedEntity("myEntity", MySource)
    builder.replaceBySource(trueSources, replacement)
    assertEquals(setOf("myEntity"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    builder.assertConsistency()
  }

  @Test
  fun `removing entity in builder`() {
    val entity = builder add NamedEntity("myEntity", MySource)
    replacement = createBuilderFrom(builder)
    replacement.removeEntity(entity)
    builder.replaceBySource(trueSources, replacement)

    builder.assertConsistency()
    assertNoNamedEntities()
    thisStateCheck { entity assert ReplaceState.Remove }
  }

  @Test
  fun `child and parent - modify parent`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(parent) {
      myName = "newProperty"
    }

    builder.replaceBySource(trueSources, replacement)

    val child = assertOneElement(builder.entities(NamedChildEntity::class.java).toList())
    assertEquals("newProperty", child.parentEntity.myName)
    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    builder.assertConsistency()
  }

  @Test
  fun `child and parent - modify child`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    val child = builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child) {
      childProperty = "newProperty"
    }

    builder.replaceBySource(trueSources, replacement)

    val updatedChild = assertOneElement(builder.entities(NamedChildEntity::class.java).toList())
    assertEquals("newProperty", updatedChild.childProperty)
    assertEquals(updatedChild, assertOneElement(builder.entities(NamedEntity::class.java).toList()).children.single())
    builder.assertConsistency()
  }

  @Test
  fun `child and parent - remove parent`() {
    val parent = builder add NamedEntity("myProperty", MySource) {
      children = listOf(NamedChildEntity("myChild", MySource))
    }

    replacement = createBuilderFrom(builder)
    replacement.removeEntity(parent)

    rbsAllSources()

    builder.assertConsistency()
    assertNoNamedEntities()
    assertNoNamedChildEntities()
    thisStateCheck {
      parent assert ReplaceState.Remove
    }
  }

  @Test
  fun `child and parent - change parent for child`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    val parent2 = builder add NamedEntity("anotherProperty", MySource)
    val child = builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child) {
      this.parentEntity = parent2
    }

    builder.replaceBySource(trueSources, replacement)

    builder.assertConsistency()
    val parents = builder.entities(NamedEntity::class.java).toList()
    assertTrue(parents.single { it.myName == "myProperty" }.children.none())
    assertEquals(child.childProperty, parents.single { it.myName == "anotherProperty" }.children.single().childProperty)
    thisStateCheck {
      parent assert ReplaceState.Relink.Relabel(listOf(NamedChildEntity::class.java))
      parent2 assert ReplaceState.Relabel
      child assert ReplaceState.Remove
    }
    replaceWithCheck {
      child assert ReplaceWithState.SubtreeMoved
      parent2 assert ReplaceWithState.Relabel(parent2.base.id)
    }
  }

  @Test
  fun `child and parent - change parent for child - 2`() {
    val parent = builder add NamedEntity("myProperty", AnotherSource)
    val parent2 = builder add NamedEntity("anotherProperty", MySource)
    val child = builder add NamedChildEntity("myChild", AnotherSource) {
      this.parentEntity = parent2
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child) {
      this.parentEntity = parent
    }

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
  }

  @Test
  fun `child and parent - change parent for child - 3`() {
    // Difference with the test above: different initial parent
    val parent = builder add NamedEntity("myProperty", AnotherSource)
    val parent2 = builder add NamedEntity("anotherProperty", MySource)
    val child = builder add NamedChildEntity("myChild", AnotherSource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child) {
      this.parentEntity = parent2
    }

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
    val parents = builder.entities(NamedEntity::class.java).toList()
    assertTrue(parents.single { it.myName == "anotherProperty" }.children.none())
    assertEquals(child, parents.single { it.myName == "myProperty" }.children.single())
  }

  @Test
  fun `child and parent - remove child`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    val child = builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.removeEntity(child)

    builder.replaceBySource(trueSources, replacement)

    assertEmpty(builder.entities(NamedChildEntity::class.java).toList())
    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    assertEmpty(builder.entities(NamedEntity::class.java).single().children.toList())
    builder.assertConsistency()
  }

  @Test
  fun `fail - child and parent - different source for parent`() {
    replacement = createBuilderFrom(builder)
    val parent = replacement add NamedEntity("myProperty", AnotherSource)
    replacement add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    builder.replaceBySource({ it is MySource }, replacement)

    assertTrue(builder.entities(NamedEntity::class.java).toList().isEmpty())
    assertTrue(builder.entities(NamedChildEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `child and parent - two children of different sources`() {
    val parent = builder add NamedEntity("Property", AnotherSource)
    builder add NamedChildEntity("MySourceChild", MySource) {
      this.parentEntity = parent
    }
    replacement = createBuilderFrom(builder)
    replacement add NamedChildEntity("AnotherSourceChild", AnotherSource) {
      this.parentEntity = parent
    }

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    val child = assertOneElement(builder.entities(NamedChildEntity::class.java).toList())
    assertEquals("MySourceChild", child.childProperty)
  }

  @Test
  fun `child and parent - trying to remove parent and leave child`() {
    val parentEntity = builder add NamedEntity("prop", AnotherSource)
    builder add NamedChildEntity("data", MySource) {
      this.parentEntity = parentEntity
    }
    replacement = createBuilderFrom(builder)
    replacement.removeEntity(parentEntity)

    builder.replaceBySource({ it is AnotherSource }, replacement)

    builder.assertConsistency()
    assertNoNamedEntities()
    assertNoNamedChildEntities()

    thisStateCheck {
      parentEntity assert ReplaceState.Remove
    }
  }

  @Test
  fun `child and parent - different source for child`() {
    replacement = createBuilderFrom(builder)
    val parent = replacement add NamedEntity("myProperty", MySource)
    replacement add NamedChildEntity("myChild", AnotherSource) {
      this.parentEntity = parent
    }

    builder.replaceBySource({ it is MySource }, replacement)

    assertEmpty(builder.entities(NamedChildEntity::class.java).toList())
    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    assertEmpty(builder.entities(NamedEntity::class.java).single().children.toList())
    builder.assertConsistency()
  }

  @Test
  fun `remove child of different source`() {
    val parent = builder add NamedEntity("data", AnotherSource)
    val child = builder add NamedChildEntity("data", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.removeEntity(child)

    builder.replaceBySource({ it is MySource }, replacement)
  }

  @Test
  @Ignore
  fun `entity with soft reference`() {
    val named = builder.addNamedEntity("MyName")
    builder.addWithSoftLinkEntity(named.persistentId)
    resetChanges()
    builder.assertConsistency()

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(named) {
      this.myName = "NewName"
    }

    replacement.assertConsistency()

    builder.replaceBySource(trueSources, replacement)
    assertEquals("NewName", assertOneElement(builder.entities(NamedEntity::class.java).toList()).myName)
    assertEquals("NewName", assertOneElement(builder.entities(WithSoftLinkEntity::class.java).toList()).link.presentableName)

    builder.assertConsistency()
  }

  @Test
  fun `entity with soft reference remove reference`() {
    val named = builder.addNamedEntity("MyName")
    val linked = builder.addWithListSoftLinksEntity("name", listOf(named.persistentId))
    resetChanges()
    builder.assertConsistency()

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(linked) {
      this.links = mutableListOf()
    }

    replacement.assertConsistency()

    builder.replaceBySource(trueSources, replacement)

    builder.assertConsistency()
  }

  @Test
  @Ignore
  fun `replace by source with composite id`() {
    replacement = createEmptyBuilder()
    val namedEntity = replacement.addNamedEntity("MyName")
    val composedEntity = replacement.addComposedIdSoftRefEntity("AnotherName", namedEntity.persistentId)
    replacement.addComposedLinkEntity(composedEntity.persistentId)

    replacement.assertConsistency()
    builder.replaceBySource(trueSources, replacement)
    builder.assertConsistency()

    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    assertOneElement(builder.entities(ComposedIdSoftRefEntity::class.java).toList())
    assertOneElement(builder.entities(ComposedLinkEntity::class.java).toList())
  }

  /*
    Not sure if this should work this way. We can track persistentId changes in builder, but what if we perform replaceBySource with storage?
    @Test
    fun `entity with soft reference - linked has wrong source`() {
      val builder = PEntityStorageBuilder.create()
      val named = builder.addNamedEntity("MyName")
      val linked = builder.addWithSoftLinkEntity(named.persistentId, AnotherSource)
      resetChanges()
      builder.assertConsistency()

      replacement = PEntityStorageBuilder.from(builder)
      replacement.modifyEntity(ModifiableNamedEntity::class.java, named) {
        this.name = "NewName"
      }

      replacement.assertConsistency()

      builder.replaceBySource({ it is MySource }, replacement)
      assertEquals("NewName", assertOneElement(builder.entities(NamedEntity::class.java).toList()).name)
      assertEquals("NewName", assertOneElement(builder.entities(WithSoftLinkEntity::class.java).toList()).link.presentableName)

      builder.assertConsistency()
    }
  */

  @Ignore("Not supported yet")
  @Test
  fun `trying to create two similar persistent ids`() {
    val namedEntity = builder.addNamedEntity("MyName", source = AnotherSource)
    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(namedEntity) {
      this.myName = "AnotherName"
    }

    replacement.addNamedEntity("MyName", source = MySource)

    builder.replaceBySource({ it is MySource }, replacement)
  }

  @Test
  fun `changing parent`() {
    val parentEntity = builder add NamedEntity("data", MySource)
    val childEntity = builder add NamedChildEntity("data", AnotherSource) {
      this.parentEntity = parentEntity
    }

    replacement = createBuilderFrom(builder)

    val anotherParent = replacement add NamedEntity("Another", MySource)
    replacement.modifyEntity(childEntity) {
      this.parentEntity = anotherParent
    }

    builder.replaceBySource({ it is MySource }, replacement)
  }

  @Test
  fun `replace same entity with persistent id and different sources`() {
    val name = "Hello"
    builder.addNamedEntity(name, source = MySource)
    replacement = createEmptyBuilder()
    replacement.addNamedEntity(name, source = AnotherSource)

    builder.replaceBySource({ it is AnotherSource }, replacement)

    assertEquals(1, builder.entities(NamedEntity::class.java).toList().size)
    assertEquals(AnotherSource, builder.entities(NamedEntity::class.java).single().entitySource)
  }

  @Test
  fun `replace dummy parent entity by real entity`() {
    val namedParent = builder.addNamedEntity("name", "foo", MyDummyParentSource)
    builder.addNamedChildEntity(namedParent, "fooChild", AnotherSource)

    replacement = createEmptyBuilder()
    replacement.addNamedEntity("name", "bar", MySource)
    builder.replaceBySource({ it is MySource || it is MyDummyParentSource }, replacement)

    assertEquals("bar", builder.entities(NamedEntity::class.java).single().additionalProperty)
    val child = builder.entities(NamedChildEntity::class.java).single()
    assertEquals("fooChild", child.childProperty)
    assertEquals("bar", child.parentEntity.additionalProperty)
  }

  // TODO Dummy are not supported
  //@Test
  //fun `do not replace real parent entity by dummy entity`() {
  //  val namedParent = builder.addNamedEntity("name", "foo", MySource)
  //  builder.addNamedChildEntity(namedParent, "fooChild", AnotherSource)
  //
  //  replacement = createEmptyBuilder()
  //  replacement.addNamedEntity("name", "bar", MyDummyParentSource)
  //  builder.replaceBySource({ it is MySource || it is MyDummyParentSource }, replacement)
  //
  //  assertEquals("foo", builder.entities(NamedEntity::class.java).single().additionalProperty)
  //  val child = builder.entities(NamedChildEntity::class.java).single()
  //  assertEquals("fooChild", child.childProperty)
  //  assertEquals("foo", child.parentEntity.additionalProperty)
  //}

  // TODO Dummy are not supported
  //@Test
  //fun `do not replace real parent entity by dummy entity but replace children`() {
  //  val namedParent = builder.addNamedEntity("name", "foo", MySource)
  //  builder.addNamedChildEntity(namedParent, "fooChild", MySource)
  //
  //  replacement = createEmptyBuilder()
  //  val newParent = replacement.addNamedEntity("name", "bar", MyDummyParentSource)
  //  replacement.addNamedChildEntity(newParent, "barChild", MySource)
  //  builder.replaceBySource({ it is MySource || it is MyDummyParentSource }, replacement)
  //
  //  assertEquals("foo", builder.entities(NamedEntity::class.java).single().additionalProperty)
  //  val child = builder.entities(NamedChildEntity::class.java).single()
  //  assertEquals("barChild", child.childProperty)
  //  assertEquals("foo", child.parentEntity.additionalProperty)
  //}

  private infix fun <T : WorkspaceEntity> MutableEntityStorage.add(entity: T): T {
    this.addEntity(entity)
    return entity
  }

  private val trueSources: (EntitySource) -> Boolean = { true }

  @Test
  fun `replace parents with completely different children 1`() {
    builder add NamedEntity("PrimaryName", MySource) {
      additionalProperty = "Initial"
      children = listOf(
        NamedChildEntity("PrimaryChild", MySource)
      )
    }

    replacement = createEmptyBuilder()
    replacement add NamedEntity("PrimaryName", MySource) {
      additionalProperty = "Update"
      children = listOf(
        NamedChildEntity("PrimaryChild", MySource)
      )
    }

    builder.replaceBySource(trueSources, replacement)
    val parentEntity = builder.entities(NamedEntity::class.java).single()
    assertEquals("Update", parentEntity.additionalProperty)
    assertEquals("PrimaryChild", parentEntity.children.single().childProperty)
  }

  @Test
  fun `replace parents with completely different children`() {
    val parentEntity = builder.addNamedEntity("PrimaryParent", additionalProperty = "Initial", source = AnotherSource)
    builder.addNamedChildEntity(parentEntity, "PrimaryChild", source = MySource)
    builder.addNamedEntity("SecondaryParent", source = AnotherSource)

    replacement = createEmptyBuilder()
    replacement.addNamedEntity("PrimaryParent", additionalProperty = "New", source = AnotherSource)
    val anotherParentEntity = replacement.addNamedEntity("SecondaryParent2", source = AnotherSource)
    replacement.addNamedChildEntity(anotherParentEntity, source = MySource)

    builder.replaceBySource({ it is AnotherSource }, replacement)

    val primaryChild = builder.entities(NamedChildEntity::class.java).find { it.childProperty == "PrimaryChild" }!!
    assertEquals("PrimaryParent", primaryChild.parentEntity.myName)
    assertEquals("New", primaryChild.parentEntity.additionalProperty)
  }

  @Test
  @Ignore("Well, this case is explicitly not supported yet")
  fun `replace oneToOne connection with partial move`() {
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)

    replacement = createEmptyBuilder()
    val anotherParent = replacement.addOoParentEntity(source = AnotherSource)
    replacement.addOoChildEntity(anotherParent)

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
  }

  @Test
  fun `replace oneToOne connection with partial move and pid`() {
    val parentEntity = builder.addOoParentWithPidEntity(source = AnotherSource)
    builder.addOoChildForParentWithPidEntity(parentEntity, source = MySource)

    replacement = createEmptyBuilder()
    val anotherParent = replacement.addOoParentWithPidEntity(source = MySource)
    replacement.addOoChildForParentWithPidEntity(anotherParent, source = MySource)

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()

    assertNotNull(builder.entities(OoParentWithPidEntity::class.java).single().childOne)

    thisStateCheck {
      parentEntity assert ReplaceState.Relink.Relabel(listOf(OoChildForParentWithPidEntity::class.java))
      parentEntity.childOne!! assert ReplaceState.Relabel
    }

    replaceWithCheck {
      anotherParent assert ReplaceWithState.Relabel(parentEntity.base.id)
    }
  }

  @Test
  fun `replace with unmatching tree`() {
    val entity = builder add NamedEntity("Data", MySource) {
      children = listOf(
        NamedChildEntity("ChildData", MySource),
        NamedChildEntity("AnotherChildData", MySource),
      )
    }

    val newEntity = replacement add NamedEntity("Data", AnotherSource) {
      children = listOf(
        NamedChildEntity("ChildData", AnotherSource),
        NamedChildEntity("AnotherChildData", AnotherSource),
      )
    }

    rbsMySources()

    builder.assertConsistency()
    assertNoNamedEntities()
    assertNoNamedChildEntities()

    thisStateCheck {
      entity assert ReplaceState.Remove
      entity.children.forEach { child ->
        child assert ReplaceState.Remove
      }
    }

    replaceWithCheck {
      newEntity assert ReplaceWithState.NoChangeTraceLost
    }
  }

  @Test
  fun `no changes in root`() {
    val entity = builder add NamedEntity("Data", AnotherSource) {
      children = listOf(
        NamedChildEntity("data", MySource)
      )
    }
    val newEntity = replacement add NamedEntity("Data", AnotherSource)

    rbsMySources()

    builder.assertConsistency()
    assertSingleNameEntity("Data")
    assertNoNamedChildEntities()

    thisStateCheck {
      entity assert ReplaceState.Relink.NoChange(listOf(NamedChildEntity::class.java))
    }

    replaceWithCheck {
      newEntity assert ReplaceWithState.NoChange(entity.base.id)
    }
  }

  @Test
  fun `move two children`() {
    builder add NamedEntity("Data", AnotherSource)
    replacement add NamedEntity("Data", AnotherSource) {
      children = listOf(
        NamedChildEntity("data1", MySource),
        NamedChildEntity("data2", MySource),
      )
    }

    rbsMySources()

    builder.assertConsistency()
    val parentEntity = assertSingleNameEntity("Data")

    assertEquals(setOf("data1", "data2"), parentEntity.children.map { it.childProperty }.toSet())
  }

  @Test
  fun `remove two children`() {
    builder add NamedEntity("Data", AnotherSource) {
      children = listOf(
        NamedChildEntity("data1", MySource),
        NamedChildEntity("data2", MySource),
      )
    }
    replacement add NamedEntity("Data", AnotherSource)

    rbsMySources()

    builder.assertConsistency()
    assertSingleNameEntity("Data")
    assertNoNamedChildEntities()
  }

  @Test
  fun `rbs for multiple parents but no actual multiple parents`() {
    builder add TreeMultiparentRootEntity("data", MySource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", MySource),
        TreeMultiparentLeafEntity("info2", MySource),
      )
    }

    replacement add TreeMultiparentRootEntity("data", MySource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", MySource),
        TreeMultiparentLeafEntity("info2", MySource),
      )
    }

    rbsAllSources()

    builder.assertConsistency()
  }

  @Test
  fun `rbs for deep chain`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", MySource))
        },
      )
    }
    val thisRoot = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceRoot = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal2", MySource))
        },
      )
    }

    rbsMySources()

    builder.assertConsistency()

    assertEquals("internal2", builder.entities(TreeMultiparentRootEntity::class.java).single().children.single().children.single().data)

    thisStateCheck {
      thisRoot assert ReplaceState.Relink.NoChange(listOf(TreeMultiparentLeafEntity::class.java))
      thisRoot.children.single() assert ReplaceState.Relink.NoChange(listOf(TreeMultiparentLeafEntity::class.java))
      thisRoot.children.single().children.single() assert ReplaceState.Remove
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
      replaceRoot.children.single() assert ReplaceWithState.NoChange(thisRoot.children.single().base.id)
      replaceRoot.children.single().children.single() assert ReplaceWithState.SubtreeMoved
    }
  }

  @Test
  fun `rbs for deep chain 2`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", AnotherSource))
        },
      )
    }
    val thisRoot = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceRoot = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal2", MySource))
        },
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val endChildren = builder.entities(TreeMultiparentRootEntity::class.java).single().children.single().children
    assertTrue(endChildren.any { it.data == "internal2" })
    assertTrue(endChildren.any { it.data == "internal" })

    thisStateCheck {
      thisRoot assert ReplaceState.NoChange
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
      replaceRoot.children.single() assert ReplaceWithState.NoChange(thisRoot.children.single().base.id)
      replaceRoot.children.single().children.single() assert ReplaceWithState.SubtreeMoved
    }
  }

  @Test
  fun `rbs for deep chain 3`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", MySource))
        },
      )
    }
    val thisRoot = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceRoot = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal2", AnotherSource))
        },
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val endChildren = builder.entities(TreeMultiparentRootEntity::class.java).single().children.single().children
    assertTrue(endChildren.isEmpty())

    thisStateCheck {
      thisRoot assert ReplaceState.Relink.NoChange(listOf(TreeMultiparentLeafEntity::class.java))
      thisRoot.children.single() assert ReplaceState.Relink.NoChange(listOf(TreeMultiparentLeafEntity::class.java))
      thisRoot.children.single().children.single() assert ReplaceState.Remove
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
    }
  }

  @Test
  fun `rbs for deep chain 4`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", MySource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", AnotherSource))
        },
      )
    }
    val thisRoot = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceRoot = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", MySource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal2", AnotherSource))
        },
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val endChildren = builder.entities(TreeMultiparentRootEntity::class.java).single().children.single().children
    assertEquals("internal", endChildren.single().data)

    thisStateCheck {
      thisRoot assert ReplaceState.Relink.NoChange(listOf(TreeMultiparentLeafEntity::class.java))
      thisRoot.children.single() assert ReplaceState.Relabel
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
      replaceRoot.children.single() assert ReplaceWithState.Relabel(thisRoot.children.single().base.id)
    }
  }

  @Test
  fun `rbs for multiple parents`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", MySource))
        },
        TreeMultiparentLeafEntity("info2", AnotherSource),
      )
    }

    replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal2", MySource))
        },
        TreeMultiparentLeafEntity("info2", AnotherSource),
      )
    }

    rbsMySources()

    builder.assertConsistency()
  }

  private inner class ThisStateChecker {
    infix fun WorkspaceEntity.assert(state: ReplaceState) {
      val thisState = engine.targetState[this.base.id]
      assertNotNull(thisState)
      if (state is ReplaceState.Relink) {
        assertEquals(state.javaClass, thisState.javaClass)
        assertEquals(state.linkedChildren.toHashSet(), (thisState as ReplaceState.Relink).linkedChildren.toHashSet())
      }
      else {
        assertEquals(state, thisState)
      }
    }
  }

  private inner class ReplaceStateChecker {
    infix fun WorkspaceEntity.assert(state: ReplaceWithState) {
      assertEquals(state, engine.replaceWithState[this.base.id])
    }
  }

  private fun thisStateCheck(checks: ThisStateChecker.() -> Unit) {
    ThisStateChecker().checks()
  }

  private fun replaceWithCheck(checks: ReplaceStateChecker.() -> Unit) {
    ReplaceStateChecker().checks()
  }

  private val WorkspaceEntity.base: WorkspaceEntityBase
    get() = this as WorkspaceEntityBase

  private fun assertNoNamedEntities() {
    assertTrue(builder.entities(NamedEntity::class.java).toList().isEmpty())
  }

  private fun assertSingleNameEntity(name: String): NamedEntity {
    val entities = builder.entities(NamedEntity::class.java).toList()
    assertEquals(1, entities.size)
    assertEquals(name, entities.single().myName)
    return entities.single()
  }

  private fun assertNoNamedChildEntities() {
    assertTrue(builder.entities(NamedChildEntity::class.java).toList().isEmpty())
  }

  private fun rbsAllSources() {
    builder.replaceBySource(trueSources, replacement)
  }

  private fun rbsMySources() {
    builder.replaceBySource({ it is MySource }, replacement)
  }

  private fun resetChanges() {
    builder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl
    builder.useNewRbs = true
    builder.keepLastRbsEngine = true
  }

  private val engine: ReplaceBySourceAsTree
    get() = builder.engine as ReplaceBySourceAsTree
}
