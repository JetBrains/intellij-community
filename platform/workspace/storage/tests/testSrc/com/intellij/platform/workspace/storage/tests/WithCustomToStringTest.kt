// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.testEntities.entities.MySource
import com.intellij.platform.workspace.storage.testEntities.entities.WithCustomToStringEntity
import com.intellij.platform.workspace.storage.testEntities.entities.modifyWithCustomToStringEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class WithCustomToStringTest {
  @Test
  fun `test custom toString`() {
    val builder = createEmptyBuilder()

    val entityName = "name"
    val entityVersion = 23
    val entityBuilder = WithCustomToStringEntity(entityName, entityVersion, MySource)
    val entity = builder.addEntity(entityBuilder)
    assertEquals("Custom entity toString: $entityName & $entityVersion", entity.toString())
    assertEquals("Custom entity toString: $entityName & $entityVersion", entityBuilder.toString())
    
    val newName = "newName"
    val snapshot = builder.toSnapshot()
    val anotherBuilder = snapshot.toBuilder()
    val newEntity = anotherBuilder.modifyWithCustomToStringEntity(entity) {
      myName = newName
    }
    assertEquals("Custom entity toString: $newName & $entityVersion", newEntity.toString())
    assertEquals("Custom entity toString: $newName & $entityVersion", "$newEntity")
  }
}
