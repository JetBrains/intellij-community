package com.intellij.workspace.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceBySourceTest {
  @Test
  fun `add entity`() {
    val builder = TypedEntityStorageBuilder.create()
    builder.addSampleEntity("hello2", SampleEntitySource("2"))
    val replacement = TypedEntityStorageBuilder.create()
    replacement.addSampleEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({it == SampleEntitySource("1")}, replacement)
    builder.checkConsistency()
    assertEquals(setOf("hello1", "hello2"), builder.entities(SampleEntity::class).mapTo(HashSet()) {it.stringProperty})
  }

  @Test
  fun `remove entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val source1 = SampleEntitySource("1")
    builder.addSampleEntity("hello1", source1)
    builder.addSampleEntity("hello2", SampleEntitySource("2"))
    builder.replaceBySource({it == source1}, TypedEntityStorageBuilder.create())
    builder.checkConsistency()
    assertEquals("hello2", builder.singleSampleEntity().stringProperty)
  }

  @Test
  fun `modify entity`() {
    val builder = TypedEntityStorageBuilder.create()
    val source1 = SampleEntitySource("1")
    builder.addSampleEntity("hello1", source1)
    builder.addSampleEntity("hello2", SampleEntitySource("2"))
    val replacement = TypedEntityStorageBuilder.create()
    replacement.addSampleEntity("updated", source1)
    builder.replaceBySource({it == source1}, replacement)
    builder.checkConsistency()
    assertEquals(setOf("hello2", "updated"), builder.entities(SampleEntity::class).mapTo(HashSet()) {it.stringProperty})
  }

  @Test
  fun `multiple sources`() {
    val builder = TypedEntityStorageBuilder.create()
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val sourceB = SampleEntitySource("b")
    builder.addSampleEntity("a", sourceA1)
    builder.addSampleEntity("b", sourceB)
    val replacement = TypedEntityStorageBuilder.create()
    replacement.addSampleEntity("new", sourceA2)
    builder.replaceBySource({ it is SampleEntitySource && it.name.startsWith("a") }, replacement)
    builder.checkConsistency()
    assertEquals(setOf("b", "new"), builder.entities(SampleEntity::class).mapTo(HashSet()) { it.stringProperty })
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