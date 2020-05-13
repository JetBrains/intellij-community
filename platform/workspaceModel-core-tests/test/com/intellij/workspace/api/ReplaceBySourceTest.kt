package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceBySourceTest {
  @Test
  fun `add entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    builder.addSampleEntity("hello2", SampleEntitySource("2"))
    val replacement = TypedEntityStorageBuilder.createProxy()
    replacement.addSampleEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({it == SampleEntitySource("1")}, replacement)
    builder.checkConsistency()
    assertEquals(setOf("hello1", "hello2"), builder.entities(SampleEntity::class.java).mapTo(HashSet()) {it.stringProperty})
  }

  @Test
  fun `add parent and child entities`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    builder.addParentEntity("parent2", SampleEntitySource("2"))
    val original = builder.toStorage()
    val replacement = TypedEntityStorageBuilder.createProxy()
    val parent = replacement.addParentEntity("parent", SampleEntitySource("1"))
    replacement.addChildWithOptionalParentEntity(parentEntity = parent, childProperty = "child", source = SampleEntitySource("1"))
    builder.replaceBySource({it == SampleEntitySource("1")}, replacement)
    builder.checkConsistency()
    assertEquals("parent", builder.entities(ChildWithOptionalParentEntity::class.java).single().optionalParent!!.parentProperty)
    val changes = builder.collectChanges(original)
    @Suppress("UNCHECKED_CAST")
    val childFromLog = (changes.getValue(ChildWithOptionalParentEntity::class.java).single() as EntityChange.Added<ChildWithOptionalParentEntity>).entity
    assertEquals("child", childFromLog.childProperty)
    assertEquals("parent", childFromLog.optionalParent!!.parentProperty)
  }

  @Test
  fun `modify entity with optional reference`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    builder.addChildWithOptionalParentEntity(null, "hello", SampleEntitySource("1"))
    val replacement = TypedEntityStorageBuilder.createProxy()
    replacement.addChildWithOptionalParentEntity(null, "hello2", SampleEntitySource("1"))
    builder.replaceBySource({it == SampleEntitySource("1")}, replacement)
    builder.checkConsistency()
    assertEquals("hello2", builder.entities(ChildWithOptionalParentEntity::class.java).single().childProperty)
  }

  @Test
  fun `remove entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val source1 = SampleEntitySource("1")
    builder.addSampleEntity("hello1", source1)
    builder.addSampleEntity("hello2", SampleEntitySource("2"))
    builder.replaceBySource({it == source1}, TypedEntityStorageBuilder.createProxy())
    builder.checkConsistency()
    assertEquals("hello2", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `modify entity`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val source1 = SampleEntitySource("1")
    builder.addSampleEntity("hello1", source1)
    builder.addSampleEntity("hello2", SampleEntitySource("2"))
    val replacement = TypedEntityStorageBuilder.createProxy()
    replacement.addSampleEntity("updated", source1)
    builder.replaceBySource({it == source1}, replacement)
    builder.checkConsistency()
    assertEquals(setOf("hello2", "updated"), builder.entities(SampleEntity::class.java).mapTo(HashSet()) {it.stringProperty})
  }

  @Test
  fun `multiple sources`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val sourceB = SampleEntitySource("b")
    builder.addSampleEntity("a", sourceA1)
    builder.addSampleEntity("b", sourceB)
    val replacement = TypedEntityStorageBuilder.createProxy()
    replacement.addSampleEntity("new", sourceA2)
    builder.replaceBySource({ it is SampleEntitySource && it.name.startsWith("a") }, replacement)
    builder.checkConsistency()
    assertEquals(setOf("b", "new"), builder.entities(SampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `work with different entity sources`() {
    val builder = TypedEntityStorageBuilder.createProxy()
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val parentEntity = builder.addParentEntity(source = sourceA1)
    val replacement = TypedEntityStorageBuilder.fromProxy(builder)
    replacement.addNoDataChildEntity(parentEntity = parentEntity, source = sourceA2)
    builder.replaceBySource({ it == sourceA2}, replacement)
    builder.checkConsistency()
    assertEquals(1, builder.toStorage().entities(ParentEntity::class.java).toList().size)
    assertEquals(1, builder.toStorage().entities(NoDataChildEntity::class.java).toList().size)
  }

  @Test
  fun classifyByEquals() {
    fun assertClassify(onlyIn1: List<String>, onlyIn2: List<String>, equal: List<Pair<String, String>>,
                       c1: List<String>, c2: List<String>) {
      val actual = ProxyBasedEntityStorage.classifyByEquals(
        c1, c2, { it[0].hashCode() }, { it[0].hashCode() }, { s1, s2 -> s1.take(2) == s2.take(2) })

      assertEquals(onlyIn1, actual.onlyIn1)
      assertEquals(onlyIn2, actual.onlyIn2)
      assertEquals(equal, actual.equal)
    }

    assertClassify(listOf("a1"), listOf("a2"), listOf("a31" to "a33", "a32" to "a34", "a4" to "a4"),
                   listOf("a1", "a31", "a32", "a4"), listOf("a2", "a33", "a34", "a4"))
    assertClassify(listOf("a", "b"), listOf("c", "d"), listOf("e" to "e"),
                   listOf("a", "b", "e"), listOf("e", "c", "d"))
  }
}