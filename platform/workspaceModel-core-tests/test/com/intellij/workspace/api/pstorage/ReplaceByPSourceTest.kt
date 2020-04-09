// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.SampleEntitySource
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.pstorage.PEntityStorageBuilder.Companion.classifyByEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaceByPSourceTest {
  @Test
  fun `add entity`() {
    val builder = PEntityStorageBuilder.create()
    builder.addPSampleEntity("hello2", SampleEntitySource("2"))
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("hello1", SampleEntitySource("1"))
    builder.replaceBySource({ it == SampleEntitySource("1") }, replacement)
    assertEquals(setOf("hello1", "hello2"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `remove entity`() {
    val builder = PEntityStorageBuilder.create()
    val source1 = SampleEntitySource("1")
    builder.addPSampleEntity("hello1", source1)
    builder.addPSampleEntity("hello2", SampleEntitySource("2"))
    builder.replaceBySource({ it == source1 }, PEntityStorageBuilder.create())
    assertEquals("hello2", builder.singlePSampleEntity().stringProperty)
  }

  @Test
  fun `modify entity`() {
    val builder = PEntityStorageBuilder.create()
    val source1 = SampleEntitySource("1")
    builder.addPSampleEntity("hello1", source1)
    builder.addPSampleEntity("hello2", SampleEntitySource("2"))
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("updated", source1)
    builder.replaceBySource({ it == source1 }, replacement)
    assertEquals(setOf("hello2", "updated"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `multiple sources`() {
    val builder = PEntityStorageBuilder.create()
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val sourceB = SampleEntitySource("b")
    builder.addPSampleEntity("a", sourceA1)
    builder.addPSampleEntity("b", sourceB)
    val replacement = PEntityStorageBuilder.create()
    replacement.addPSampleEntity("new", sourceA2)
    builder.replaceBySource({ it is SampleEntitySource && it.name.startsWith("a") }, replacement)
    assertEquals(setOf("b", "new"), builder.entities(PSampleEntity::class.java).mapTo(HashSet()) { it.stringProperty })
  }

  @Test
  fun `work with different entity sources`() {
    val builder = PEntityStorageBuilder.create()
    val sourceA1 = SampleEntitySource("a1")
    val sourceA2 = SampleEntitySource("a2")
    val parentEntity = builder.addPParentEntity(source = sourceA1)
    val replacement = PEntityStorageBuilder.from(builder)
    replacement.addPNoDataChildEntity(parentEntity = parentEntity, source = sourceA2)
    builder.replaceBySource({ it == sourceA2 }, replacement)
    assertEquals(1, builder.toStorage().entities(PParentEntity::class.java).toList().size)
    assertEquals(1, builder.toStorage().entities(PNoDataChildEntity::class.java).toList().size)
  }

  @Test
  fun classifyByEquals() {
    fun assertClassify(onlyIn1: List<String>,
                       onlyIn2: List<String>,
                       equal: List<Pair<String, String>>,
                       c1: List<String>,
                       c2: List<String>) {
      val actual = classifyByEquals(c1, c2, { it[0].hashCode() }, { it[0].hashCode() },
                                    { s1, s2 -> s1.take(2) == s2.take(2) })

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