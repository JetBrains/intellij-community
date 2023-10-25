// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.testEntities.entities.*
import com.intellij.platform.workspace.storage.toBuilder
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import java.util.*
import kotlin.test.*

class ReplaceBySourceTest {

  private lateinit var builder: MutableEntityStorageImpl
  private lateinit var replacement: MutableEntityStorageImpl

  @BeforeEach
  fun setUp(info: RepetitionInfo) {
    builder = createEmptyBuilder()
    replacement = createEmptyBuilder()
    builder.keepLastRbsEngine = true
    // Random returns same result for nextInt(2) for the first 4095 seeds, so we generated random seed
    builder.upgradeEngine = { it.shuffleEntities = Random(info.currentRepetition.toLong()).nextLong() }
  }

  @RepeatedTest(10)
  fun `add entity`() {
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    replacement = createEmptyBuilder()
    replacement add NamedEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({ it == SampleEntitySource("1") }, replacement)
    assertEquals(setOf("hello1", "hello2"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `remove entity`() {
    val source1 = SampleEntitySource("1")
    builder add NamedEntity("hello1", source1)
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    builder.replaceBySource({ it == source1 }, createEmptyBuilder())
    assertEquals("hello2", builder.entities(NamedEntity::class.java).single().myName)
    builder.assertConsistency()
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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
    replaceWithCheck { parent3 assert ReplaceWithState.ElementMoved }
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `empty storages`() {
    val builder2 = createEmptyBuilder()

    builder.replaceBySource({ true }, builder2)
    assertTrue(builder.collectChanges().isEmpty())
    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `replace with empty storage`() {
    val parent1 = builder add NamedEntity("data1", MySource)
    val parent2 = builder add NamedEntity("data2", MySource)
    resetChanges()
    builder.toSnapshot()

    builder.replaceBySource({ true }, createEmptyBuilder())
    val collectChanges = builder.collectChanges()
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

  @RepeatedTest(10)
  fun `add entity with false source`() {
    builder add NamedEntity("hello2", SampleEntitySource("2"))
    resetChanges()
    replacement = createEmptyBuilder()
    replacement add NamedEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({ false }, replacement)
    assertEquals(setOf("hello2"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    assertTrue(builder.collectChanges().isEmpty())
    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `entity modification`() {
    val entity = builder add NamedEntity("hello2", MySource)
    replacement = createBuilderFrom(builder)
    val replacementEntity = entity.createReference<NamedEntity>().resolve(replacement)!!
    val modified = replacement.modifyEntity(replacementEntity) {
      myName = "Hello Alex"
    }

    rbsAllSources()

    builder.assertConsistency()
    assertSingleNameEntity("Hello Alex")

    thisStateCheck {
      entity assert ReplaceState.Remove
    }

    replaceWithCheck {
      modified assert ReplaceWithState.ElementMoved
    }
  }

  @RepeatedTest(10)
  fun `adding entity in builder`() {
    replacement = createBuilderFrom(builder)
    replacement add NamedEntity("myEntity", MySource)
    rbsAllSources()
    assertEquals(setOf("myEntity"), builder.entities(NamedEntity::class.java).mapTo(HashSet()) { it.myName })
    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `removing entity in builder`() {
    val entity = builder add NamedEntity("myEntity", MySource)
    replacement = createBuilderFrom(builder)
    replacement.removeEntity(entity.from(replacement))
    rbsAllSources()

    builder.assertConsistency()
    assertNoNamedEntities()
    thisStateCheck { entity assert ReplaceState.Remove }
  }

  @RepeatedTest(10)
  fun `child and parent - modify parent`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(parent.from(replacement)) {
      myName = "newProperty"
    }

    rbsAllSources()

    val child = assertOneElement(builder.entities(NamedChildEntity::class.java).toList())
    assertEquals("newProperty", child.parentEntity.myName)
    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `child and parent - modify child`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    val child = builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child.from(replacement)) {
      childProperty = "newProperty"
    }

    rbsAllSources()

    val updatedChild = assertOneElement(builder.entities(NamedChildEntity::class.java).toList())
    assertEquals("newProperty", updatedChild.childProperty)
    assertEquals(updatedChild, assertOneElement(builder.entities(NamedEntity::class.java).toList()).children.single())
    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `child and parent - remove parent`() {
    val parent = builder add NamedEntity("myProperty", MySource) {
      children = listOf(NamedChildEntity("myChild", MySource))
    }

    replacement = createBuilderFrom(builder)
    replacement.removeEntity(parent.from(replacement))

    rbsAllSources()

    builder.assertConsistency()
    assertNoNamedEntities()
    assertNoNamedChildEntities()
    thisStateCheck {
      parent assert ReplaceState.Remove
    }
  }

  @RepeatedTest(10)
  fun `child and parent - change parent for child`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    val parent2 = builder add NamedEntity("anotherProperty", MySource)
    val child = builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child.from(replacement)) {
      this.parentEntity = parent2
    }

    // Here the original child entity is removed and a new child entity is added.
    //   I don't really like this approach, but at the moment we can't understand that child entity wan't actually changed
    rbsAllSources()

    builder.assertConsistency()
    val parents = builder.entities(NamedEntity::class.java).toList()
    assertTrue(parents.single { it.myName == "myProperty" }.children.none())
    assertTrue(parents.single { it.myName == "anotherProperty" }.children.singleOrNull() != null)

    // child is removed, so we can't access it
    //assertEquals(child.childProperty, parents.single { it.myName == "anotherProperty" }.children.single().childProperty)
    thisStateCheck {
      parent assert ReplaceState.Relabel(parent.base.id)
      parent2 assert ReplaceState.Relabel(parent2.base.id)
      child assert ReplaceState.Remove
    }
    replaceWithCheck {
      child assert ReplaceWithState.ElementMoved
      parent2 assert ReplaceWithState.Relabel(parent2.base.id)
    }
  }

  @RepeatedTest(10)
  fun `child and parent - change parent for child - 2`() {
    val parent = builder add NamedEntity("myProperty", AnotherSource)
    val parent2 = builder add NamedEntity("anotherProperty", MySource)
    val child = builder add NamedChildEntity("myChild", AnotherSource) {
      this.parentEntity = parent2
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child.from(replacement)) {
      this.parentEntity = parent
    }

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `child and parent - change parent for child - 3`() {
    // Difference with the test above: different initial parent
    val parent = builder add NamedEntity("myProperty", AnotherSource)
    val parent2 = builder add NamedEntity("anotherProperty", MySource)
    val child = builder add NamedChildEntity("myChild", AnotherSource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(child.from(replacement)) {
      this.parentEntity = parent2
    }

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
    val parents = builder.entities(NamedEntity::class.java).toList()
    assertTrue(parents.single { it.myName == "anotherProperty" }.children.none())
    assertEquals(child, parents.single { it.myName == "myProperty" }.children.single())
  }

  @RepeatedTest(10)
  fun `child and parent - remove child`() {
    val parent = builder add NamedEntity("myProperty", MySource)
    val child = builder add NamedChildEntity("myChild", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.removeEntity(child.from(replacement))

    rbsAllSources()

    assertEmpty(builder.entities(NamedChildEntity::class.java).toList())
    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    assertEmpty(builder.entities(NamedEntity::class.java).single().children.toList())
    builder.assertConsistency()
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `child and parent - trying to remove parent and leave child`() {
    val parentEntity = builder add NamedEntity("prop", AnotherSource)
    builder add NamedChildEntity("data", MySource) {
      this.parentEntity = parentEntity
    }
    replacement = createBuilderFrom(builder)
    replacement.removeEntity(parentEntity.from(replacement))

    builder.replaceBySource({ it is AnotherSource }, replacement)

    builder.assertConsistency()
    assertNoNamedEntities()
    assertNoNamedChildEntities()

    thisStateCheck {
      parentEntity assert ReplaceState.Remove
    }
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `remove child of different source`() {
    val parent = builder add NamedEntity("data", AnotherSource)
    val child = builder add NamedChildEntity("data", MySource) {
      this.parentEntity = parent
    }

    replacement = createBuilderFrom(builder)
    replacement.removeEntity(child.from(replacement))

    builder.replaceBySource({ it is MySource }, replacement)
  }

  @RepeatedTest(10)
  fun `entity with soft reference`() {
    val named = builder.addNamedEntity("MyName")
    builder.addWithSoftLinkEntity(named.symbolicId)
    resetChanges()
    builder.assertConsistency()

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(named.from(replacement)) {
      this.myName = "NewName"
    }

    replacement.assertConsistency()

    rbsAllSources()
    assertEquals("NewName", assertOneElement(builder.entities(NamedEntity::class.java).toList()).myName)
    assertEquals("NewName", assertOneElement(builder.entities(WithSoftLinkEntity::class.java).toList()).link.presentableName)

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `entity with soft reference remove reference`() {
    val named = builder.addNamedEntity("MyName")
    val linked = builder.addWithListSoftLinksEntity("name", listOf(named.symbolicId))
    resetChanges()
    builder.assertConsistency()

    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(linked.from(replacement)) {
      this.links = mutableListOf()
    }

    replacement.assertConsistency()

    rbsAllSources()

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `replace by source with composite id`() {
    replacement = createEmptyBuilder()
    val namedEntity = replacement.addNamedEntity("MyName")
    val composedEntity = replacement.addComposedIdSoftRefEntity("AnotherName", namedEntity.symbolicId)
    replacement.addComposedLinkEntity(composedEntity.symbolicId)

    replacement.assertConsistency()
    rbsAllSources()
    builder.assertConsistency()

    assertOneElement(builder.entities(NamedEntity::class.java).toList())
    assertOneElement(builder.entities(ComposedIdSoftRefEntity::class.java).toList())
    assertOneElement(builder.entities(ComposedLinkEntity::class.java).toList())
  }

  @RepeatedTest(10)
  fun `trying to create two similar persistent ids`() {
    val namedEntity = builder.addNamedEntity("MyName", source = AnotherSource)
    replacement = createBuilderFrom(builder)
    replacement.modifyEntity(namedEntity.from(replacement)) {
      this.myName = "AnotherName"
    }

    replacement.addNamedEntity("MyName", source = MySource)

    rbsMySources()

    assertEquals(MySource, builder.entities(NamedEntity::class.java).single().entitySource)
  }

  @RepeatedTest(10)
  fun `changing parent`() {
    val parentEntity = builder add NamedEntity("data", MySource)
    val childEntity = builder add NamedChildEntity("data", AnotherSource) {
      this.parentEntity = parentEntity
    }

    replacement = createBuilderFrom(builder)

    val anotherParent = replacement add NamedEntity("Another", MySource)
    replacement.modifyEntity(childEntity.from(replacement)) {
      this.parentEntity = anotherParent
    }

    builder.replaceBySource({ it is MySource }, replacement)
  }

  @RepeatedTest(10)
  fun `replace same entity with persistent id and different sources`() {
    val name = "Hello"
    builder.addNamedEntity(name, source = MySource)
    replacement = createEmptyBuilder()
    replacement.addNamedEntity(name, source = AnotherSource)

    builder.replaceBySource({ it is AnotherSource }, replacement)

    assertEquals(1, builder.entities(NamedEntity::class.java).toList().size)
    assertEquals(AnotherSource, builder.entities(NamedEntity::class.java).single().entitySource)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `do not replace real parent entity by dummy entity`() {
    val namedParent = builder.addNamedEntity("name", "foo", MySource)
    builder.addNamedChildEntity(namedParent, "fooChild", AnotherSource)

    replacement = createEmptyBuilder()
    replacement.addNamedEntity("name", "bar", MyDummyParentSource)
    builder.replaceBySource({ it is MySource || it is MyDummyParentSource }, replacement)

    assertEquals("foo", builder.entities(NamedEntity::class.java).single().additionalProperty)
    val child = builder.entities(NamedChildEntity::class.java).single()
    assertEquals("fooChild", child.childProperty)
    assertEquals("foo", child.parentEntity.additionalProperty)
  }

  @RepeatedTest(10)
  fun `do not replace real parent entity by dummy entity but replace children`() {
    val namedParent = builder.addNamedEntity("name", "foo", MySource)
    builder.addNamedChildEntity(namedParent, "fooChild", MySource)

    replacement = createEmptyBuilder()
    val newParent = replacement.addNamedEntity("name", "bar", MyDummyParentSource)
    replacement.addNamedChildEntity(newParent, "barChild", MySource)
    builder.replaceBySource({ it is MySource || it is MyDummyParentSource }, replacement)

    assertEquals("foo", builder.entities(NamedEntity::class.java).single().additionalProperty)
    val child = builder.entities(NamedChildEntity::class.java).single()
    assertEquals("barChild", child.childProperty)
    assertEquals("foo", child.parentEntity.additionalProperty)
  }

  @RepeatedTest(10)
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

    rbsAllSources()
    val parentEntity = builder.entities(NamedEntity::class.java).single()
    assertEquals("Update", parentEntity.additionalProperty)
    assertEquals("PrimaryChild", parentEntity.children.single().childProperty)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `replace oneToOne connection with partial move`() {
    val parentEntity = builder.addOoParentEntity()
    builder.addOoChildEntity(parentEntity)

    replacement = createEmptyBuilder()
    val anotherParent = replacement.addOoParentEntity(source = AnotherSource)
    replacement.addOoChildEntity(anotherParent)

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `replace oneToOne connection with partial move and pid`() {
    val parentEntity = builder.addOoParentWithPidEntity(source = AnotherSource)
    builder.addOoChildForParentWithPidEntity(parentEntity, source = MySource)

    val anotherParent = replacement.addOoParentWithPidEntity(source = MySource)
    replacement.addOoChildForParentWithPidEntity(anotherParent, source = MySource)

    builder.replaceBySource({ it is MySource }, replacement)

    builder.assertConsistency()

    assertNotNull(builder.entities(OoParentWithPidEntity::class.java).single().childOne)

    thisStateCheck {
      parentEntity assert ReplaceState.Relabel(anotherParent.base.id)
      parentEntity.childOne!! assert ReplaceState.Relabel(anotherParent.childOne!!.base.id,
                                                          setOf(ParentsRef.TargetRef(parentEntity.base.id)))
    }

    replaceWithCheck {
      anotherParent assert ReplaceWithState.Relabel(parentEntity.base.id)
    }
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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
      entity assert ReplaceState.NoChange(newEntity.base.id)
    }

    replaceWithCheck {
      newEntity assert ReplaceWithState.NoChange(entity.base.id)
    }
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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

  @RepeatedTest(10)
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
      thisRoot assert ReplaceState.NoChange(replaceRoot.base.id)
      thisRoot.children.single() assert ReplaceState.NoChange(replaceRoot.children.single().base.id)
      thisRoot.children.single().children.single() assert ReplaceState.Remove
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
      replaceRoot.children.single() assert ReplaceWithState.NoChange(thisRoot.children.single().base.id)
      replaceRoot.children.single().children.single() assert ReplaceWithState.ElementMoved
    }
  }

  @RepeatedTest(10)
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
      thisRoot assert ReplaceState.NoChange(replaceRoot.base.id)
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
      replaceRoot.children.single() assert ReplaceWithState.NoChange(thisRoot.children.single().base.id)
      replaceRoot.children.single().children.single() assert ReplaceWithState.ElementMoved
    }
  }

  @RepeatedTest(10)
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
      thisRoot assert ReplaceState.NoChange(replaceRoot.base.id)
      thisRoot.children.single() assert ReplaceState.NoChange(replaceRoot.children.single().base.id)
      thisRoot.children.single().children.single() assert ReplaceState.Remove
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
    }
  }

  @RepeatedTest(10)
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
      thisRoot assert ReplaceState.NoChange(replaceRoot.base.id)
      thisRoot.children.single() assert ReplaceState.Relabel(replaceRoot.children.single().base.id,
                                                             setOf(ParentsRef.TargetRef(thisRoot.base.id)))
    }

    replaceWithCheck {
      replaceRoot assert ReplaceWithState.NoChange(thisRoot.base.id)
      replaceRoot.children.single() assert ReplaceWithState.Relabel(thisRoot.children.single().base.id)
    }
  }

  @RepeatedTest(10)
  fun `rbs lot of children`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          children = listOf(
            TreeMultiparentLeafEntity("internal", MySource),
          )
        },
        TreeMultiparentLeafEntity("info2", AnotherSource),
        TreeMultiparentLeafEntity("info3", AnotherSource),
        TreeMultiparentLeafEntity("info4", AnotherSource),
        TreeMultiparentLeafEntity("info5", AnotherSource),
      )
    }
    builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("info1", AnotherSource) {
          children = listOf(
            TreeMultiparentLeafEntity("internal", MySource),
          )
        },
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val endChildren = builder.entities(TreeMultiparentRootEntity::class.java).single().children
    assertEquals(5, endChildren.size)
  }

  @RepeatedTest(10)
  fun `rbs for deep chain 5`() {
    builder add ModuleTestEntity("data", AnotherSource) {
      this.contentRoots = listOf(
        ContentRootTestEntity(AnotherSource) {
          this.sourceRootOrder = SourceRootTestOrderEntity("info", MySource)
          this.sourceRoots = listOf(
            SourceRootTestEntity("data", MySource)
          )
        }
      )
    }
    builder.toSnapshot().entities(ModuleTestEntity::class.java).single()

    replacement add ModuleTestEntity("data", AnotherSource) {
      this.contentRoots = listOf(
        ContentRootTestEntity(AnotherSource) {
          this.sourceRootOrder = SourceRootTestOrderEntity("info", MySource)
          this.sourceRoots = listOf(
            SourceRootTestEntity("data", MySource)
          )
        }
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val contentRoot = builder.entities(ModuleTestEntity::class.java).single().contentRoots.single()
    assertEquals("info", contentRoot.sourceRootOrder!!.data)
    assertEquals("data", contentRoot.sourceRoots.single().data)
  }

  @RepeatedTest(10)
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

  @RepeatedTest(10)
  fun `abstract entity`() {
    builder add HeadAbstractionEntity("Data", AnotherSource) {
      this.child = LeftEntity(AnotherSource) {
        this.children = listOf(
          LeftEntity(MySource),
          RightEntity(MySource),
        )
      }
    }

    replacement add HeadAbstractionEntity("Data", AnotherSource) {
      this.child = LeftEntity(AnotherSource) {
        this.children = listOf(
          MiddleEntity("info1", MySource),
          MiddleEntity("info1", MySource),
        )
      }
    }

    rbsMySources()

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `non persistent id root`() {
    val targetEntity = builder addEntity SampleEntity(false, "data", ArrayList(), HashMap(),
                                                      VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), MySource)
    val replaceWithEntity = replacement addEntity SampleEntity(false, "data", ArrayList(), HashMap(),
                                                               VirtualFileUrlManagerImpl().fromUrl("file:///tmp"), MySource)

    rbsMySources()

    builder.assertConsistency()
    thisStateCheck {
      targetEntity assert ReplaceState.Relabel(replaceWithEntity.base.id)
    }
    replaceWithCheck {
      replaceWithEntity assert ReplaceWithState.Relabel(targetEntity.base.id)
    }
  }

  @RepeatedTest(10)
  fun `different source on parent`() {
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", MySource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", MySource))
        }
      )
    }
    replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", MySource))
        }
      )
    }

    rbsMySources()

    builder.assertConsistency()
    val children = builder.entities(TreeMultiparentRootEntity::class.java).single().children
    assertTrue(children.isEmpty())
  }

  @RepeatedTest(10)
  fun `detach root parent`() {
    val internalChild = TreeMultiparentLeafEntity("internal", MySource)
    val leafsStructure = TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", AnotherSource) {
          this.children = listOf(internalChild)
        }
      )
    }
    builder add leafsStructure
    builder.modifyEntity(internalChild) {
      this.mainParent = leafsStructure
    }

    val replaceWithEntity = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("internal", MySource))
        }
      )
    }

    var rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(2, rootEntity.children.size)

    rbsMySources()

    builder.assertConsistency()
    rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(1, rootEntity.children.size)
    val childAbove = rootEntity.children.single()
    assertEquals("data", childAbove.data)
    val child = childAbove.children.single()
    assertTrue(child.mainParent == null)

    thisStateCheck {
      leafsStructure assert ReplaceState.NoChange(replaceWithEntity.base.id)
      leafsStructure.children.single() assert ReplaceState.NoChange(replaceWithEntity.children.single().base.id)
      internalChild assert ReplaceState.Relabel(replaceWithEntity.children.single().children.single().base.id,
                                                setOf(ParentsRef.TargetRef(leafsStructure.children.single().base.id)))
    }

    replaceWithCheck {
      replaceWithEntity assert ReplaceWithState.NoChange(leafsStructure.base.id)
      replaceWithEntity.children.single() assert ReplaceWithState.NoChange(leafsStructure.children.single().base.id)
      replaceWithEntity.children.single().children.single() assert ReplaceWithState.Relabel(
        leafsStructure.children.single().children.single().base.id)
    }
  }

  @RepeatedTest(10)
  fun `detach internal parent`() {
    val internalChild = TreeMultiparentLeafEntity("internal", MySource)
    val leafsStructure = TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", AnotherSource) {
          this.children = listOf(internalChild)
        }
      )
    }
    builder add leafsStructure
    builder.modifyEntity(internalChild) {
      this.mainParent = leafsStructure
    }
    val root = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceChild1 = TreeMultiparentLeafEntity("data", AnotherSource)
    val replaceChild2 = TreeMultiparentLeafEntity("internal", MySource)
    val replaceWithEntity = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        replaceChild1,
        replaceChild2,
      )
    }

    var rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(2, rootEntity.children.size)

    rbsMySources()

    builder.assertConsistency()
    rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(2, rootEntity.children.size)
    val childAbove = rootEntity.children.single { it.data == "data" }
    assertEquals("data", childAbove.data)
    val noChildren = childAbove.children.isEmpty()
    assertTrue(noChildren)

    thisStateCheck {
      root assert ReplaceState.NoChange(replaceWithEntity.base.id)
      root.children.single { it.data == "data" } assert ReplaceState.NoChange(replaceChild1.base.id)
      internalChild assert ReplaceState.Relabel(replaceChild2.base.id, setOf(ParentsRef.TargetRef(root.base.id)))
    }

    replaceWithCheck {
      replaceWithEntity assert ReplaceWithState.NoChange(root.base.id)
      replaceChild1 assert ReplaceWithState.NoChange(root.children.single { it.data == "data" }.base.id)
      replaceChild2 assert ReplaceWithState.Relabel(internalChild.base.id)
    }
  }

  @RepeatedTest(10)
  fun `detach both parents`() {
    val internalChild = TreeMultiparentLeafEntity("internal", MySource)
    val leafsStructure = TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", AnotherSource) {
          this.children = listOf(internalChild)
        }
      )
    }
    builder add leafsStructure
    builder.modifyEntity(internalChild) {
      this.mainParent = leafsStructure
    }
    val root = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceWithDataElement = TreeMultiparentLeafEntity("data", AnotherSource)
    val replaceWithEntity = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        replaceWithDataElement,
      )
    }

    var rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(2, rootEntity.children.size)

    rbsMySources()

    builder.assertConsistency()
    rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(1, rootEntity.children.size)
    val childAbove = rootEntity.children.single { it.data == "data" }
    assertEquals("data", childAbove.data)
    val noChildren = childAbove.children.isEmpty()
    assertTrue(noChildren)

    thisStateCheck {
      root assert ReplaceState.NoChange(replaceWithEntity.base.id)
      root.children.single { it.data == "data" } assert ReplaceState.NoChange(replaceWithDataElement.base.id)
      internalChild assert ReplaceState.Remove
    }

    replaceWithCheck {
      replaceWithEntity assert ReplaceWithState.NoChange(root.base.id)
      replaceWithDataElement assert ReplaceWithState.NoChange(root.children.single { it.data == "data" }.base.id)
    }
  }

  @RepeatedTest(10)
  fun `attach to root entity`() {
    val internalChild = TreeMultiparentLeafEntity("internal", MySource)
    val leafsStructure = TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", AnotherSource) {
          this.children = listOf(internalChild)
        }
      )
    }
    builder add leafsStructure
    val root = builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val replaceWithDataElement = TreeMultiparentLeafEntity("data", AnotherSource)
    val replaceWithEntity = replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        replaceWithDataElement,
      )
    }
    val internalReplacement = replacement add TreeMultiparentLeafEntity("internal", MySource) {
      this.mainParent = replaceWithEntity
      this.leafParent = replaceWithDataElement
    }

    var rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(1, rootEntity.children.size)

    rbsMySources()

    builder.assertConsistency()
    rootEntity = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals(2, rootEntity.children.size)
    val childAbove = rootEntity.children.single { it.data == "data" }
    rootEntity.children.single { it.data == "internal" }
    assertEquals("data", childAbove.data)
    val myInternalChild = childAbove.children.single()
    assertTrue(myInternalChild.data == "internal")

    thisStateCheck {
      root assert ReplaceState.NoChange(replaceWithEntity.base.id)
      root.children.single { it.data == "data" } assert ReplaceState.NoChange(replaceWithDataElement.base.id)
      internalChild assert ReplaceState.Relabel(
        internalReplacement.base.id,
        parents = setOf(ParentsRef.TargetRef(leafsStructure.base.id), ParentsRef.TargetRef(root.children.single().base.id))
      )
    }

    replaceWithCheck {
      replaceWithEntity assert ReplaceWithState.NoChange(root.base.id)
      replaceWithDataElement assert ReplaceWithState.NoChange(root.children.single { it.data == "data" }.base.id)
    }
  }

  @RepeatedTest(10)
  fun `add new root`() {
    val leafsStructure = TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        TreeMultiparentLeafEntity("data", MySource)
      )
    }
    builder add leafsStructure
    builder.toSnapshot().entities(TreeMultiparentRootEntity::class.java).single()

    val doubleRooted = TreeMultiparentLeafEntity("data", MySource)
    replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        doubleRooted
      )
    }
    replacement add TreeMultiparentRootEntity("data2", MySource) {
      children = listOf(
        TreeMultiparentLeafEntity("SomeEntity", MySource) {
          this.children = listOf(doubleRooted)
        }
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val parents = builder.entities(TreeMultiparentRootEntity::class.java).toList()
    assertEquals(2, parents.size)
    val targetLeaf = parents.single { it.data == "data" }.children.single()
    val targetLeafFromOtherSide = parents.single { it.data == "data2" }.children.single().children.single()
    assertEquals(targetLeaf.base.id, targetLeafFromOtherSide.base.id)
    assertEquals("data", targetLeaf.data)
  }

  @RepeatedTest(10)
  fun `transfer new store`() {
    val doubleRooted = TreeMultiparentLeafEntity("data", MySource)
    replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        doubleRooted
      )
    }
    replacement add TreeMultiparentRootEntity("data2", MySource) {
      children = listOf(
        TreeMultiparentLeafEntity("SomeEntity", MySource) {
          this.children = listOf(doubleRooted)
        }
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    val parents = builder.entities(TreeMultiparentRootEntity::class.java).toList()
    assertEquals(2, parents.size)
    val targetLeaf = parents.single { it.data == "data" }.children.single()
    val targetLeafFromOtherSide = parents.single { it.data == "data2" }.children.single().children.single()
    assertEquals(targetLeaf.base.id, targetLeafFromOtherSide.base.id)
    assertEquals("data", targetLeaf.data)
  }

  @RepeatedTest(10)
  fun `do not transfer to new store`() {
    val doubleRooted = TreeMultiparentLeafEntity("data", MySource)
    replacement add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        doubleRooted
      )
    }
    replacement add TreeMultiparentRootEntity("data2", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("SomeEntity", AnotherSource) {
          this.children = listOf(doubleRooted)
        }
      )
    }

    rbsMySources()

    builder.assertConsistency()

    val parents = builder.entities(TreeMultiparentRootEntity::class.java).toList()
    assertEquals(0, parents.size)
    val leafs = builder.entities(TreeMultiparentLeafEntity::class.java).toList()
    assertEquals(0, leafs.size)
  }

  @RepeatedTest(10)
  fun `unbind ref`() {
    val doubleRooted = TreeMultiparentLeafEntity("data", MySource)
    builder add TreeMultiparentRootEntity("data", AnotherSource) {
      this.children = listOf(
        doubleRooted
      )
    }
    builder add TreeMultiparentRootEntity("data2", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("SomeEntity", AnotherSource) {
          this.children = listOf(doubleRooted)
        }
      )
    }

    replacement add TreeMultiparentRootEntity("data2", AnotherSource) {
      children = listOf(
        TreeMultiparentLeafEntity("SomeEntity", AnotherSource) {
          this.children = listOf(TreeMultiparentLeafEntity("data", MySource))
        }
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    val parent = builder.entities(TreeMultiparentRootEntity::class.java).single()
    assertEquals("data2", parent.data)
    val single = parent.children.single().children.single()
    assertEquals("data", single.data)
  }

  @RepeatedTest(10)
  fun `same child`() {
    builder add NamedEntity("data", MySource) {
      children = listOf(
        NamedChildEntity("Info1", SampleEntitySource("a")),
        NamedChildEntity("Info1", SampleEntitySource("a")),
      )
    }

    replacement add NamedEntity("data", MySource) {
      children = listOf(
        NamedChildEntity("Info1", SampleEntitySource("x")),
        NamedChildEntity("Info1", SampleEntitySource("x")),
      )
    }

    builder.replaceBySource({ it is SampleEntitySource }, replacement)

    builder.assertConsistency()

    val sources = builder.entities(NamedEntity::class.java).single().children
      .map { (it.entitySource as SampleEntitySource).name }
      .toSet()
      .single()
    assertEquals("x", sources)
  }

  @RepeatedTest(10)
  fun `persistent id in the middle`() {
    builder add ModuleTestEntity("data", AnotherSource) {
      facets = listOf(
        FacetTestEntity("facet", "MyData", MySource)
      )
    }
    replacement add ModuleTestEntity("data", AnotherSource) {
      facets = listOf(
        FacetTestEntity("facet", "Very other data", MySource)
      )
    }


    rbsMySources()

    builder.assertConsistency()

    assertEquals("Very other data", builder.entities(ModuleTestEntity::class.java).single().facets.single().moreData)
  }

  @RepeatedTest(10)
  fun `persistent id in the middle 2`() {
    builder add ModuleTestEntity("data", AnotherSource) {
      facets = listOf(
        FacetTestEntity("facet", "MyData", AnotherSource)
      )
    }
    replacement add ModuleTestEntity("data", MySource) {
      facets = listOf(
        FacetTestEntity("facet", "Very other data", MySource)
      )
    }


    rbsAllSources()

    builder.assertConsistency()

    val module = builder.entities(ModuleTestEntity::class.java).single()
    assertEquals(MySource, module.entitySource)
    val facet = module.facets.single()
    assertEquals(MySource, facet.entitySource)
    assertEquals("Very other data", facet.moreData)
  }

  @RepeatedTest(10)
  fun `replace root entities without persistent id`() {
    builder add LeftEntity(AnotherSource)
    builder add LeftEntity(AnotherSource)

    replacement add LeftEntity(MySource)
    replacement add LeftEntity(MySource)


    rbsAllSources()

    builder.assertConsistency()

    val leftEntities = builder.entities(LeftEntity::class.java).toList()
    assertEquals(2, leftEntities.size)
    assertTrue(leftEntities.all { it.entitySource == MySource })
  }

  @RepeatedTest(10)
  fun `replace same entities should produce no events`() {
    builder add NamedEntity("name", MySource) {
      children = listOf(
        NamedChildEntity("info1", MySource),
        NamedChildEntity("info2", MySource),
      )
    }

    replacement add NamedEntity("name", MySource) {
      children = listOf(
        NamedChildEntity("info1", MySource),
        NamedChildEntity("info2", MySource),
      )
    }

    builder.changeLog.clear()

    rbsAllSources()

    builder.assertConsistency()

    assertEquals(0, builder.changeLog.changeLog.size)
  }

  @RepeatedTest(10)
  fun `replace same entities should produce in case of source change`() {
    builder add NamedEntity("name", MySource) {
      children = listOf(
        NamedChildEntity("info1", MySource),
        NamedChildEntity("info2", MySource),
      )
    }

    replacement add NamedEntity("name", MySource) {
      children = listOf(
        NamedChildEntity("info1", AnotherSource),
        NamedChildEntity("info2", MySource),
      )
    }

    builder.changeLog.clear()

    rbsAllSources()

    builder.assertConsistency()

    assertEquals(1, builder.changeLog.changeLog.size)
  }

  @RepeatedTest(10)
  fun `replace same entities should produce in case of source chang2e`() {
    builder add OoParentEntity("prop", MySource)

    val parent = replacement add OoParentEntity("prop", MySource)
    replacement add OoChildWithNullableParentEntity(AnotherSource) {
      this.parentEntity = parent
    }
    replacement add OoChildWithNullableParentEntity(AnotherSource) {
      this.parentEntity = parent
    }
    replacement add OoChildWithNullableParentEntity(AnotherSource) {
      this.parentEntity = parent
    }

    builder.changeLog.clear()

    builder.replaceBySource({ it is AnotherSource }, replacement)
    builder.replaceBySource({ true }, builder.toSnapshot())

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `external entity`() {
    replacement add MainEntityParentList("data", MySource) {
      this.children = listOf(AttachedEntityParentList("info", MySource))
    }

    rbsMySources()

    builder.assertConsistency()

    assertTrue(builder.entities(MainEntityParentList::class.java).single().children.isNotEmpty())
  }

  @RepeatedTest(10)
  fun `move external entity`() {
    replacement add MainEntity("data", MySource) {
      this.child = AttachedEntity("Data", MySource)
    }

    builder.changeLog.clear()

    rbsAllSources()

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `move left entity`() {
    val left0 = builder add LeftEntity(MySource)
    val left1 = builder add LeftEntity(MySource) {
      this.parentEntity = left0
    }
    builder add LeftEntity(MySource) {
      this.parentEntity = left1
    }
    builder add LeftEntity(MySource)

    val leftR0 = replacement add LeftEntity(MySource)
    val leftR1 = replacement add LeftEntity(MySource) {
      this.parentEntity = leftR0
    }
    replacement add LeftEntity(MySource) {
      this.parentEntity = leftR1
    }
    val leftR3 = replacement add LeftEntity(MySource)
    replacement add LeftEntity(MySource) {
      this.children = listOf(leftR3, leftR0)
    }

    rbsAllSources()

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `check ordering of the children as in replacement`() {
    replacement add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("one", MySource),
        NamedChildEntity("two", MySource),
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    val children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("one", children[0].childProperty)
    assertEquals("two", children[1].childProperty)
  }

  @RepeatedTest(10)
  fun `check ordering of the children as in replacement2`() {
    builder add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("unchanged", MySource),
        NamedChildEntity("two", MySource),
      )
    }

    replacement add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("one", MySource),
        NamedChildEntity("unchanged", MySource),
        NamedChildEntity("two", MySource),
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    val children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("one", children[0].childProperty)
    assertEquals("unchanged", children[1].childProperty)
  }

  @RepeatedTest(10)
  fun `check ordering of the children as in replacement3`() {
    builder add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("one", MySource),
        NamedChildEntity("two", MySource),
      )
    }

    var children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("one", children[0].childProperty)
    assertEquals("two", children[1].childProperty)

    replacement add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("two", MySource),
        NamedChildEntity("one", MySource),
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("two", children[0].childProperty)
    assertEquals("one", children[1].childProperty)
  }

  @RepeatedTest(10)
  fun `check ordering of the children as in replacement after add`() {
    builder add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("one", MySource),
      )
    }

    var children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("one", children[0].childProperty)

    replacement add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("two", MySource),
        NamedChildEntity("one", MySource),
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("two", children[0].childProperty)
    assertEquals("one", children[1].childProperty)
  }

  @RepeatedTest(10)
  fun `check ordering of the children as in replacement after add 2`() {
    builder add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("one", MySource),
      )
    }

    var children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("one", children[0].childProperty)

    replacement add NamedEntity("Name", MySource) {
      this.children = listOf(
        NamedChildEntity("one", MySource),
        NamedChildEntity("two", MySource),
      )
    }

    rbsAllSources()

    builder.assertConsistency()

    children = builder.entities(NamedEntity::class.java).single().children
    assertEquals("one", children[0].childProperty)
    assertEquals("two", children[1].childProperty)
  }

  @RepeatedTest(10)
  fun `test rbs to itself with multiple parents and same children`() {
    val root11 = ContentRootTestEntity(MySource)
    val root12 = ContentRootTestEntity(MySource)

    builder add ModuleTestEntity("MyModule", MySource) {
      this.contentRoots = listOf(root11, root12)
    }
    builder add ProjectModelTestEntity("", Descriptor(""), MySource) {
      this.contentRoot = root11
    }

    val root21 = ContentRootTestEntity(MySource)
    val root22 = ContentRootTestEntity(MySource)
    replacement add ModuleTestEntity("MyModule", MySource) {
      this.contentRoots = listOf(root21, root22)
    }
    replacement add ProjectModelTestEntity("", Descriptor(""), MySource) {
      this.contentRoot = root21
    }

    rbsAllSources()

    builder.assertConsistency()
  }

  @RepeatedTest(10)
  fun `test replaceBySource with two equal entities referring to each other`() {
    val superParent = builder addEntity ChainedParentEntity(MySource)
    val parent = builder addEntity ChainedEntity("data", MySource) {
      this.generalParent = superParent
    }
    builder addEntity ChainedEntity("data", AnotherSource) {
      this.parent = parent
      this.generalParent = superParent
    }

    val anotherBuilder = builder.toSnapshot().toBuilder()

    assertNull(builder.entities(ChainedEntity::class.java).single { it.entitySource == MySource }.parent)
    assertNotEquals(AnotherSource,
                    builder.entities(ChainedEntity::class.java).single { it.entitySource == AnotherSource }.parent!!.entitySource)

    builder.replaceBySource({ true }, anotherBuilder)

    assertNull(builder.entities(ChainedEntity::class.java).single { it.entitySource == MySource }.parent)
    assertNotEquals(AnotherSource,
                    builder.entities(ChainedEntity::class.java).single { it.entitySource == AnotherSource }.parent!!.entitySource)
  }

  @RepeatedTest(10)
  fun `replace child to itself keeps it's order`() {
    val middleChild = builder addEntity MiddleEntity("", AnotherSource)
    val rightChild = builder addEntity RightEntity(MySource)
    builder addEntity LeftEntity(MySource) {
      this.children = listOf(middleChild, rightChild)
    }

    val anotherBuilder = builder.toSnapshot().toBuilder()

    builder.replaceBySource({ it is AnotherSource }, anotherBuilder)

    val children = builder.entities(LeftEntity::class.java).single().children
    assertIs<MiddleEntity>(children[0])
    assertIs<RightEntity>(children[1])
  }

  private inner class ThisStateChecker {
    infix fun WorkspaceEntity.assert(state: ReplaceState) {
      val thisState = engine.targetState[this.base.id]
      assertNotNull(thisState)

      assertEquals(state, thisState)
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
    builder.replaceBySource({ true }, replacement)
  }

  private fun rbsMySources() {
    builder.replaceBySource({ it is MySource }, replacement)
  }

  private fun resetChanges() {
    builder = builder.toSnapshot().toBuilder() as MutableEntityStorageImpl
    builder.keepLastRbsEngine = true
  }

  private val engine: ReplaceBySourceAsTree
    get() = builder.engine as ReplaceBySourceAsTree

  private infix fun <T : WorkspaceEntity> MutableEntityStorage.add(entity: T): T {
    this.addEntity(entity)
    return entity
  }
}
