// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.DefaultValueEntity
import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DefaultValueTest {

  @Test
  fun simpleCheckDefaultValue() {
    val entity = DefaultValueEntity("", MySource)
    assertEquals(true, entity.isGenerated)
    assertEquals("Another Text", entity.anotherName)
  }

  @Test
  fun checkChangedDefaultValue() {
    val entity = DefaultValueEntity("", MySource) {
      isGenerated = false
    }
    assertEquals(false, entity.isGenerated)
    assertEquals("Another Text", entity.anotherName)
    entity as DefaultValueEntity.Builder
    entity.anotherName = "Simple Text"
    assertEquals("Simple Text", entity.anotherName)
  }

  @Test
  fun checkValueInitialized() {
    val entity = DefaultValueEntity("name", SampleEntitySource("test")) {
      isGenerated = false
    }
    val builder = createEmptyBuilder()
    builder.addEntity(entity)

    val snapshot = builder.toSnapshot()
    val defaultValueEntity = snapshot.entities(DefaultValueEntity::class.java).single()
    assertEquals(false, defaultValueEntity.isGenerated)
    assertEquals("Another Text", defaultValueEntity.anotherName)
  }
}