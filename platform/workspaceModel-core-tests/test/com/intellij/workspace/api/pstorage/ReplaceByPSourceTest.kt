// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.workspace.api.EntityChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
 *
 * different connection types
 * persistent id
 * soft links
 */
class ReplaceByPSourceTest {
  @Test
  fun `add entity`() {
    val builder = PEntityStorageBuilder.create()
    builder.addPSampleEntity("hello2", PSampleEntitySource("2"))
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("hello1", PSampleEntitySource("1"))
    builder.replaceBySource({ it == PSampleEntitySource("1") }, replacement)
    assertEquals(setOf("hello1", "hello2"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
    builder.assertConsistency()
  }

  @Test
  fun `remove entity`() {
    val builder = PEntityStorageBuilder.create()
    val source1 = PSampleEntitySource("1")
    builder.addPSampleEntity("hello1", source1)
    builder.addPSampleEntity("hello2", PSampleEntitySource("2"))
    builder.replaceBySource({ it == source1 }, PEntityStorageBuilder.create())
    assertEquals("hello2", builder.singlePSampleEntity().stringProperty)
    builder.assertConsistency()
  }

  @Test
  fun `remove and add entity`() {
    val builder = PEntityStorageBuilder.create()
    val source1 = PSampleEntitySource("1")
    builder.addPSampleEntity("hello1", source1)
    builder.addPSampleEntity("hello2", PSampleEntitySource("2"))
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("updated", source1)
    builder.replaceBySource({ it == source1 }, replacement)
    assertEquals(setOf("hello2", "updated"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
    builder.assertConsistency()
  }

  @Test
  fun `multiple sources`() {
    val builder = PEntityStorageBuilder.create()
    val sourceA1 = PSampleEntitySource("a1")
    val sourceA2 = PSampleEntitySource("a2")
    val sourceB = PSampleEntitySource("b")
    builder.addPSampleEntity("a", sourceA1)
    builder.addPSampleEntity("b", sourceB)
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("new", sourceA2)
    builder.replaceBySource({ it is PSampleEntitySource && it.name.startsWith("a") }, replacement)
    assertEquals(setOf("b", "new"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
    builder.assertConsistency()
  }

  @Test
  fun `work with different entity sources`() {
    val builder = PEntityStorageBuilder.create()
    val sourceA1 = PSampleEntitySource("a1")
    val sourceA2 = PSampleEntitySource("a2")
    val parentEntity = builder.addPParentEntity(source = sourceA1)
    val replacement = PEntityStorageBuilder.from(builder)
    replacement.addPNoDataChildEntity(parentEntity = parentEntity, source = sourceA2)
    builder.replaceBySource({ it == sourceA2 }, replacement)
    assertEquals(1, builder.toStorage().entities(PParentEntity::class.java).toList().size)
    assertEquals(1, builder.toStorage().entities(PNoDataChildEntity::class.java).toList().size)
    builder.assertConsistency()
  }

  @Test
  fun `empty storages`() {
    val builder = PEntityStorageBuilder.create()
    val builder2 = PEntityStorageBuilder.create()

    builder.replaceBySource({ true }, builder2)
    assertTrue(builder.collectChanges(PEntityStorageBuilder.create()).isEmpty())
    builder.assertConsistency()
  }

  @Test
  fun `replace with empty storage`() {
    val builder = PEntityStorageBuilder.create()
    builder.addPSampleEntity("data1")
    builder.addPSampleEntity("data2")
    builder.resetChanges()
    val originalStorage = builder.toStorage()

    builder.replaceBySource({ true }, PEntityStorageBuilder.create())
    val collectChanges = builder.collectChanges(originalStorage)
    assertEquals(1, collectChanges.size)
    assertEquals(2, collectChanges.values.single().size)
    assertTrue(collectChanges.values.single().all { it is EntityChange.Removed<*> })
    builder.assertConsistency()
    assertTrue(builder.entities(PSampleEntity::class.java).toList().isEmpty())
  }

  @Test
  fun `add entity with false source`() {
    val builder = PEntityStorageBuilder.create()
    builder.addPSampleEntity("hello2", PSampleEntitySource("2"))
    builder.resetChanges()
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("hello1", PSampleEntitySource("1"))
    builder.replaceBySource({ false }, replacement)
    assertEquals(setOf("hello2"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
    assertTrue(builder.collectChanges(PEntityStorageBuilder.create()).isEmpty())
    builder.assertConsistency()
  }

  @Test
  fun `entity modification`() {
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPSampleEntity("hello2")
    val replacement = PEntityStorageBuilder.from(builder)
    replacement.modifyEntity(ModifiablePSampleEntity::class.java, entity) {
      stringProperty = "Hello Alex"
    }
    builder.replaceBySource({ true }, replacement)
    assertEquals(setOf("Hello Alex"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
    builder.assertConsistency()
  }

  @Test
  fun `adding entity in builder`() {
    val builder = PEntityStorageBuilder.create()
    val replacement = PEntityStorageBuilder.from(builder)
    replacement.addPSampleEntity("myEntity")
    builder.replaceBySource({ true }, replacement)
    assertEquals(setOf("myEntity"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
    builder.assertConsistency()
  }

  @Test
  fun `removing entity in builder`() {
    val builder = PEntityStorageBuilder.create()
    val entity = builder.addPSampleEntity("myEntity")
    val replacement = PEntityStorageBuilder.from(builder)
    replacement.removeEntity(entity)
    builder.replaceBySource({ true }, replacement)
    assertTrue(builder.entities(PSampleEntity::class.java).toList().isEmpty())
    builder.assertConsistency()
  }

  @Test
  fun `child and parent - modify parent`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity("myProperty")
    builder.addPChildEntity(parent, "myChild")

    val replacement = PEntityStorageBuilder.from(builder)
    replacement.modifyEntity(ModifiablePParentEntity::class.java, parent) {
      parentProperty = "newProperty"
    }

    builder.replaceBySource({ true }, replacement)

    val child = assertOneElement(builder.entities(PChildEntity::class.java).toList())
    assertEquals("newProperty", child.parent.parentProperty)
    assertOneElement(builder.entities(PParentEntity::class.java).toList())
    builder.assertConsistency()
  }

  @Test
  fun `child and parent - modify child`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity("myProperty")
    val child = builder.addPChildEntity(parent, "myChild")

    val replacement = PEntityStorageBuilder.from(builder)
    replacement.modifyEntity(ModifiablePChildEntity::class.java, child) {
      childProperty = "newProperty"
    }

    builder.replaceBySource({ true }, replacement)

    val updatedChild = assertOneElement(builder.entities(PChildEntity::class.java).toList())
    assertEquals("newProperty", updatedChild.childProperty)
    assertEquals(updatedChild, assertOneElement(builder.entities(PParentEntity::class.java).toList()).children.single())
    builder.assertConsistency()
  }

  @Test
  fun `child and parent - remove parent`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity("myProperty")
    val child = builder.addPChildEntity(parent, "myChild")

    val replacement = PEntityStorageBuilder.from(builder)
    replacement.removeEntity(parent)

    builder.replaceBySource({ true }, replacement)

    assertEmpty(builder.entities(PChildEntity::class.java).toList())
    assertEmpty(builder.entities(PParentEntity::class.java).toList())
    builder.assertConsistency()
  }

  @Test
  fun `child and parent - remove child`() {
    val builder = PEntityStorageBuilder.create()
    val parent = builder.addPParentEntity("myProperty")
    val child = builder.addPChildEntity(parent, "myChild")

    val replacement = PEntityStorageBuilder.from(builder)
    replacement.removeEntity(child)

    builder.replaceBySource({ true }, replacement)

    assertEmpty(builder.entities(PChildEntity::class.java).toList())
    assertOneElement(builder.entities(PParentEntity::class.java).toList())
    assertEmpty(builder.entities(PParentEntity::class.java).single().children.toList())
    builder.assertConsistency()
  }

  @Test(expected = IllegalStateException::class)
  fun `fail - child and parent - different source for parent`() {
    val builder = PEntityStorageBuilder.create()
    val replacement = PEntityStorageBuilder.from(builder)
    val parent = replacement.addPParentEntity("myProperty", source = AnotherSource)
    val child = replacement.addPChildEntity(parent, "myChild")

    builder.replaceBySource({ it is PSampleEntitySource }, replacement)
  }

  @Test
  fun `child and parent - different source for child`() {
    val builder = PEntityStorageBuilder.create()
    val replacement = PEntityStorageBuilder.from(builder)
    val parent = replacement.addPParentEntity("myProperty")
    val child = replacement.addPChildEntity(parent, "myChild", source = AnotherSource)

    builder.replaceBySource({ it is PSampleEntitySource }, replacement)

    assertEmpty(builder.entities(PChildEntity::class.java).toList())
    assertOneElement(builder.entities(PParentEntity::class.java).toList())
    assertEmpty(builder.entities(PParentEntity::class.java).single().children.toList())
    builder.assertConsistency()
  }
}