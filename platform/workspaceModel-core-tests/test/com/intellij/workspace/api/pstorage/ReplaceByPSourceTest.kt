// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import com.intellij.workspace.api.SampleEntitySource
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
}